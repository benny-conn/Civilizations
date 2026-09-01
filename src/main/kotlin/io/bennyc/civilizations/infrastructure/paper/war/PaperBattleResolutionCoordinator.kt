package io.bennyc.civilizations.infrastructure.paper.war

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.damage.DamageResolutionBasis
import io.bennyc.civilizations.application.damage.FinalBlockObservation
import io.bennyc.civilizations.application.damage.GenerateDamageReport
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BattleDamageReport
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.infrastructure.paper.BattleResolutionRules
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairCoordinator
import io.bennyc.civilizations.infrastructure.paper.protection.PaperLandProtectionCoordinator
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.time.Clock
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Bridges a durable RESOLVING battle to its immutable damage report and terminal state.
 * World observations stay on the Paper thread, are bounded per tick, never generate
 * chunks, and are passed inward as framework-neutral snapshots. A timeout can seal its
 * report without inventing an outcome; surrender/admin outcomes may close after sealing.
 */
class PaperBattleResolutionCoordinator(
    private val plugin: Plugin,
    private val runtime: CivilizationsRuntime,
    private val server: Server,
    private val rules: BattleResolutionRules,
    private val logger: Logger,
    private val repairCoordinator: PaperRepairCoordinator,
    private val landProtectionCoordinator: PaperLandProtectionCoordinator,
    private val clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    private val queue = LinkedHashMap<BattleId, PendingResolution>()
    private val pendingStarts = LinkedHashMap<BattleId, PendingResolution>()
    private val expiring = mutableSetOf<BattleId>()
    private var active: ActiveResolution? = null
    private var storageBusy = false
    private var closed = false
    private var repairWorldWorkSuspended = false
    private var heldChunk: HeldChunk? = null
    private var pendingChunkLoad: PendingChunkLoad? = null
    private val chunkFailures = mutableMapOf<BattleId, String>()
    private val metrics = MutableResolutionMetrics()
    private val task: BukkitTask = server.scheduler.runTaskTimer(
        plugin,
        Runnable(::tick),
        1L,
        1L,
    )

    fun forceResolve(
        battleId: BattleId,
        outcome: BattleOutcome,
        completion: (PaperBattleResolutionOutcome) -> Unit,
    ) = requestExplicitResolution(battleId, outcome, allowActive = true, completion)

    fun continueSurrender(
        battleId: BattleId,
        outcome: BattleOutcome,
        completion: (PaperBattleResolutionOutcome) -> Unit = {},
    ) = requestExplicitResolution(battleId, outcome, allowActive = false, completion)

    /** Requeues safe work after runtime startup; ambiguous timeout/admin outcomes stay open. */
    fun recover(state: CivilizationsRuntimeState.Ready) {
        val activeSeason = state.activeSeason ?: return
        activeSeason.battles
            .filter { it.status == BattleStatus.RESOLVING }
            .forEach { battle ->
                val outcome = activeSeason.battleSurrenders[battle.id]?.requestedOutcome
                    ?: activeSeason.battleCombatStates[battle.id]?.requestedOutcome
                enqueue(PendingResolution(battle.id, outcome))
            }
    }

    fun metricsSummary(): String =
        "queued=${queue.size}, observed=${metrics.observed}, sealed=${metrics.sealed}, " +
            "closed=${metrics.closed}, sealOnly=${metrics.sealOnly}, " +
            "chunkLoads=${metrics.chunkLoads}, unavailable=${metrics.unavailable}, " +
            "observationsPerTick=${rules.observationsPerTick}"

    override fun close() {
        if (closed) return
        closed = true
        task.cancel()
        val unavailable = PaperBattleResolutionOutcome.Unavailable("The server is stopping")
        pendingStarts.values.forEach { pending -> pending.complete(unavailable) }
        queue.values.forEach { pending -> pending.complete(unavailable) }
        active?.pending?.complete(unavailable)
        pendingStarts.clear()
        queue.clear()
        active = null
        releaseChunk()
        resumeRepairWorldWork()
    }

    private fun requestExplicitResolution(
        battleId: BattleId,
        outcome: BattleOutcome,
        allowActive: Boolean,
        completion: (PaperBattleResolutionOutcome) -> Unit,
    ) {
        if (closed) {
            return completion(PaperBattleResolutionOutcome.Unavailable("Resolution is stopped"))
        }
        mergeIntoPending(battleId, outcome, completion)?.let { rejection ->
            return completion(PaperBattleResolutionOutcome.Rejected(rejection))
        }
        if (isPending(battleId)) return

        val ready = runtime.state as? CivilizationsRuntimeState.Ready
            ?: return completion(
                PaperBattleResolutionOutcome.Unavailable("Civilizations is not ready"),
            )
        val battle = ready.activeSeason?.battles?.singleOrNull { it.id == battleId }
            ?: return completion(
                PaperBattleResolutionOutcome.Rejected("Battle $battleId does not exist"),
            )
        when (battle.status) {
            BattleStatus.ACTIVE -> {
                if (!allowActive) {
                    return completion(
                        PaperBattleResolutionOutcome.Rejected(
                            "Battle $battleId must already be resolving after surrender",
                        ),
                    )
                }
                val pending = PendingResolution(
                    battleId = battleId,
                    requestedOutcome = outcome,
                    completions = mutableListOf(completion),
                )
                pendingStarts[battleId] = pending
                runtime.submitMutation(
                    operation = { wars.beginResolution(battleId, force = true) },
                ) { mutation -> finishBeginResolution(pending, mutation) }
            }
            BattleStatus.RESOLVING -> enqueue(
                PendingResolution(
                    battleId,
                    outcome,
                    mutableListOf(completion),
                ),
            )
            BattleStatus.CLOSED -> prepareClosedRecovery(battle, outcome, completion)
            BattleStatus.CANCELLED -> completion(
                PaperBattleResolutionOutcome.Rejected("Battle $battleId is CANCELLED"),
            )
        }
    }

    /**
     * Returns a rejection only when an existing explicit request chose another outcome.
     * A seal-only timeout/recovery request is upgraded in place by an explicit outcome.
     */
    private fun mergeIntoPending(
        battleId: BattleId,
        outcome: BattleOutcome,
        completion: (PaperBattleResolutionOutcome) -> Unit,
    ): String? {
        val pending = pendingStarts[battleId] ?: queue[battleId]
            ?: active?.pending?.takeIf { it.battleId == battleId }
            ?: return null
        val existing = pending.requestedOutcome
        if (existing != null && existing != outcome) {
            return "Battle $battleId is already resolving as $existing"
        }
        pending.requestedOutcome = outcome
        pending.completions += completion
        return null
    }

    private fun isPending(battleId: BattleId): Boolean =
        battleId in pendingStarts || battleId in queue || active?.pending?.battleId == battleId

    private fun finishBeginResolution(
        pending: PendingResolution,
        mutation: RuntimeMutationOutcome<Battle>,
    ) {
        pendingStarts.remove(pending.battleId)
        if (closed) {
            return pending.complete(
                PaperBattleResolutionOutcome.Unavailable("Resolution is stopped"),
            )
        }
        when (mutation) {
            is RuntimeMutationOutcome.Completed -> when (val result = mutation.result) {
                is ApplicationResult.Applied,
                is ApplicationResult.Unchanged,
                -> enqueue(pending)
                is ApplicationResult.Rejected -> pending.complete(
                    PaperBattleResolutionOutcome.Rejected(result.failure.description),
                )
            }
            is RuntimeMutationOutcome.NotReady -> pending.complete(
                PaperBattleResolutionOutcome.Unavailable("Civilizations is not ready"),
            )
            is RuntimeMutationOutcome.Failed -> pending.complete(
                PaperBattleResolutionOutcome.Failed(mutation.failure),
            )
        }
    }

    private fun enqueue(pending: PendingResolution) {
        if (closed) {
            return pending.complete(
                PaperBattleResolutionOutcome.Unavailable("Resolution is stopped"),
            )
        }
        val current = queue[pending.battleId]
            ?: active?.pending?.takeIf { it.battleId == pending.battleId }
        if (current != null) {
            val requested = pending.requestedOutcome
            if (current.requestedOutcome == null) current.requestedOutcome = requested
            if (requested != null && current.requestedOutcome != requested) {
                return pending.complete(
                    PaperBattleResolutionOutcome.Rejected(
                        "Battle ${pending.battleId} is already resolving as " +
                            current.requestedOutcome,
                    ),
                )
            }
            current.completions += pending.completions
            return
        }
        if (queue.size + (if (active == null) 0 else 1) >= MAX_PENDING_RESOLUTIONS) {
            metrics.unavailable++
            return pending.complete(
                PaperBattleResolutionOutcome.Unavailable(
                    "Battle resolution is busy; try again shortly",
                ),
            )
        }
        queue[pending.battleId] = pending
    }

    private fun completeAlreadyClosed(
        battle: Battle,
        requestedOutcome: BattleOutcome,
        completion: (PaperBattleResolutionOutcome) -> Unit,
    ) {
        if (battle.outcome != requestedOutcome) {
            return completion(
                PaperBattleResolutionOutcome.Rejected(
                    "Battle ${battle.id} already closed with ${battle.outcome}",
                ),
            )
        }
        runtime.submitDamageReportOperation(
            operation = { loadResolutionBasis(battle.id) },
        ) { outcome ->
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied ->
                        completeClosedBasis(battle, result.value, completion)
                    is ApplicationResult.Unchanged ->
                        completeClosedBasis(battle, result.value, completion)
                    is ApplicationResult.Rejected -> completion(
                        PaperBattleResolutionOutcome.Rejected(result.failure.description),
                    )
                }
                is RuntimeMutationOutcome.NotReady -> completion(
                    PaperBattleResolutionOutcome.Unavailable("Civilizations is not ready"),
                )
                is RuntimeMutationOutcome.Failed -> completion(
                    PaperBattleResolutionOutcome.Failed(outcome.failure),
                )
            }
        }
    }

    private fun prepareClosedRecovery(
        battle: Battle,
        requestedOutcome: BattleOutcome,
        completion: (PaperBattleResolutionOutcome) -> Unit,
    ) {
        if (battle.outcome != requestedOutcome) {
            return completion(
                PaperBattleResolutionOutcome.Rejected(
                    "Battle ${battle.id} already closed with ${battle.outcome}",
                ),
            )
        }
        val pending = PendingResolution(
            battle.id,
            requestedOutcome,
            mutableListOf(completion),
        )
        pendingStarts[battle.id] = pending
        runtime.submitMutation(
            operation = { wars.reopenReportlessClosure(battle.id, requestedOutcome) },
        ) { outcome ->
            pendingStarts.remove(battle.id)
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> enqueue(pending)
                    is ApplicationResult.Unchanged ->
                        completeAlreadyClosed(result.value, requestedOutcome, pending::complete)
                    is ApplicationResult.Rejected -> pending.complete(
                        PaperBattleResolutionOutcome.Rejected(result.failure.description),
                    )
                }
                is RuntimeMutationOutcome.NotReady -> pending.complete(
                    PaperBattleResolutionOutcome.Unavailable("Civilizations is not ready"),
                )
                is RuntimeMutationOutcome.Failed -> pending.complete(
                    PaperBattleResolutionOutcome.Failed(outcome.failure),
                )
            }
        }
    }

    private fun completeClosedBasis(
        battle: Battle,
        basis: DamageResolutionBasis,
        completion: (PaperBattleResolutionOutcome) -> Unit,
    ) {
        val report = basis.sealedReport
        if (report == null) {
            completion(
                PaperBattleResolutionOutcome.Rejected(
                    "Battle ${battle.id} is closed without a sealed damage report",
                ),
            )
        } else {
            completion(
                PaperBattleResolutionOutcome.Completed(
                    PaperBattleResolutionResult(battle, report, closed = true),
                ),
            )
        }
    }

    private fun tick() {
        if (closed) return
        try {
            beginExpiredBattles()
            if (storageBusy) return
            val scan = active
            if (scan == null) {
                beginNext()
            } else {
                tickScan(scan)
            }
        } catch (failure: Throwable) {
            logger.log(Level.SEVERE, "Unexpected battle-resolution failure", failure)
            active?.let { fail(it.pending, PaperBattleResolutionOutcome.Failed(failure)) }
        }
    }

    /** Removes combat eligibility at the absolute deadline even with zero players online. */
    private fun beginExpiredBattles() {
        if (expiring.isNotEmpty()) return
        val ready = runtime.state as? CivilizationsRuntimeState.Ready ?: return
        val now = clock.instant()
        val battle = ready.activeSeason?.battles
            .orEmpty()
            .asSequence()
            .filter { it.status == BattleStatus.ACTIVE && it.endsAt <= now }
            .filter { it.id !in expiring && !isPending(it.id) }
            .firstOrNull() ?: return
        expiring += battle.id
        runtime.submitMutation(
            operation = { combat.beginTimeoutResolution(battle.id) },
        ) { outcome ->
            expiring -= battle.id
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> {
                        val requested = result.value.requestedOutcome
                        logger.info(
                            if (requested == null) {
                                "Legacy expired battle entered outcome-neutral resolution"
                            } else {
                                "Expired battle entered resolution as $requested"
                            },
                        )
                        // Runtime refresh recovers every expired battle in one transaction,
                        // not only the one that caused this wakeup.
                        recover(outcome.state)
                    }
                    is ApplicationResult.Unchanged -> {
                        val requested = result.value.requestedOutcome
                        logger.info(
                            if (requested == null) {
                                "Legacy expired battle entered outcome-neutral resolution"
                            } else {
                                "Expired battle entered resolution as $requested"
                            },
                        )
                        recover(outcome.state)
                    }
                    is ApplicationResult.Rejected -> logger.warning(
                        result.failure.description,
                    )
                }
                is RuntimeMutationOutcome.NotReady -> Unit
                is RuntimeMutationOutcome.Failed -> logger.log(
                    Level.SEVERE,
                    "Could not begin expiry resolution for battle ${battle.id}",
                    outcome.failure,
                )
            }
        }
    }

    private fun beginNext() {
        val pending = queue.entries.firstOrNull()?.also { queue.remove(it.key) }?.value ?: return
        storageBusy = true
        runtime.submitDamageReportOperation(
            operation = { loadResolutionBasis(pending.battleId) },
        ) { outcome ->
            storageBusy = false
            if (closed) {
                return@submitDamageReportOperation pending.complete(
                    PaperBattleResolutionOutcome.Unavailable("Resolution is stopped"),
                )
            }
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> beginBasis(pending, result.value)
                    is ApplicationResult.Unchanged -> beginBasis(pending, result.value)
                    is ApplicationResult.Rejected -> fail(
                        pending,
                        PaperBattleResolutionOutcome.Rejected(result.failure.description),
                    )
                }
                is RuntimeMutationOutcome.NotReady -> fail(
                    pending,
                    PaperBattleResolutionOutcome.Unavailable("Civilizations is not ready"),
                )
                is RuntimeMutationOutcome.Failed -> fail(
                    pending,
                    PaperBattleResolutionOutcome.Failed(outcome.failure),
                )
            }
        }
    }

    private fun beginBasis(pending: PendingResolution, basis: DamageResolutionBasis) {
        basis.sealedReport?.let { report ->
            return finishSealed(pending, basis.battle, report)
        }
        val ordered = basis.journal.sortedWith(RESOLUTION_ORDER)
        if (ordered.isEmpty()) {
            seal(pending, basis.battle, emptyList())
        } else {
            suspendRepairWorldWork()
            active = ActiveResolution(pending, basis.battle, ordered)
        }
    }

    private fun tickScan(scan: ActiveResolution) {
        chunkFailures.remove(scan.pending.battleId)?.let { description ->
            metrics.unavailable++
            return fail(
                scan.pending,
                PaperBattleResolutionOutcome.Unavailable(description),
            )
        }
        var remaining = rules.observationsPerTick
        while (remaining > 0 && scan.index < scan.orderedChanges.size) {
            val change = scan.orderedChanges[scan.index]
            when (val access = ensureChunk(scan.pending.battleId, change.position)) {
                ChunkAccess.Ready -> Unit
                ChunkAccess.Waiting -> return
                is ChunkAccess.Failed -> {
                    metrics.unavailable++
                    return fail(
                        scan.pending,
                        PaperBattleResolutionOutcome.Unavailable(access.description),
                    )
                }
            }
            val world = world(change.position.worldId)
                ?: return fail(
                    scan.pending,
                    PaperBattleResolutionOutcome.Unavailable(
                        "World ${change.position.worldId} is unavailable",
                    ),
                )
            val finalState = SimpleBlockSnapshot(
                world.getBlockAt(change.position.x, change.position.y, change.position.z)
                    .blockData
                    .getAsString(false),
            )
            scan.observations += FinalBlockObservation(change.id, finalState)
            scan.index++
            remaining--
            metrics.observed++
        }
        if (scan.index == scan.orderedChanges.size) {
            releaseChunk(scan.pending.battleId)
            active = null
            resumeRepairWorldWork()
            seal(scan.pending, scan.battle, scan.observations.toList())
        }
    }

    private fun seal(
        pending: PendingResolution,
        battle: Battle,
        observations: List<FinalBlockObservation>,
    ) {
        storageBusy = true
        runtime.submitDamageReportOperation(
            operation = { generate(GenerateDamageReport(battle.id, observations)) },
        ) { outcome ->
            storageBusy = false
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> {
                        metrics.sealed++
                        finishSealed(pending, battle, result.value)
                    }
                    is ApplicationResult.Unchanged -> finishSealed(pending, battle, result.value)
                    is ApplicationResult.Rejected -> fail(
                        pending,
                        PaperBattleResolutionOutcome.Rejected(result.failure.description),
                    )
                }
                is RuntimeMutationOutcome.NotReady -> fail(
                    pending,
                    PaperBattleResolutionOutcome.Unavailable("Civilizations is not ready"),
                )
                is RuntimeMutationOutcome.Failed -> fail(
                    pending,
                    PaperBattleResolutionOutcome.Failed(outcome.failure),
                )
            }
        }
    }

    private fun finishSealed(
        pending: PendingResolution,
        battle: Battle,
        report: BattleDamageReport,
    ) {
        val requestedOutcome = pending.requestedOutcome
        if (requestedOutcome == null) {
            metrics.sealOnly++
            logger.info(
                "Battle ${battle.id} damage report sealed with " +
                    "${report.eligibleChangeCount} eligible changes; awaiting explicit outcome",
            )
            pending.complete(
                PaperBattleResolutionOutcome.Completed(
                    PaperBattleResolutionResult(battle, report, closed = false),
                ),
            )
            return
        }
        storageBusy = true
        runtime.submitMutation(
            operation = { wars.resolve(battle.id, requestedOutcome) },
        ) { outcome ->
            storageBusy = false
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> completeClosedBattle(
                        pending,
                        result.value,
                        report,
                        outcome.state,
                    )
                    is ApplicationResult.Unchanged -> completeClosedBattle(
                        pending,
                        result.value,
                        report,
                        outcome.state,
                    )
                    is ApplicationResult.Rejected -> fail(
                        pending,
                        PaperBattleResolutionOutcome.Rejected(result.failure.description),
                    )
                }
                is RuntimeMutationOutcome.NotReady -> fail(
                    pending,
                    PaperBattleResolutionOutcome.Unavailable("Civilizations is not ready"),
                )
                is RuntimeMutationOutcome.Failed -> fail(
                    pending,
                    PaperBattleResolutionOutcome.Failed(outcome.failure),
                )
            }
        }
    }

    private fun completeClosedBattle(
        pending: PendingResolution,
        battle: Battle,
        report: BattleDamageReport,
        state: CivilizationsRuntimeState.Ready,
    ) {
        metrics.closed++
        announceClosed(battle, report, state)
        pending.complete(
            PaperBattleResolutionOutcome.Completed(
                PaperBattleResolutionResult(
                    battle = battle,
                    report = report,
                    closed = true,
                ),
            ),
        )
    }

    private fun announceClosed(
        battle: Battle,
        report: BattleDamageReport,
        state: CivilizationsRuntimeState.Ready,
    ) {
        val message = Component.text("[Civilizations] ", NamedTextColor.DARK_PURPLE)
            .append(
                Component.text(
                    "Battle ${battle.id} closed with ${battle.outcome}; " +
                        "${report.eligibleChangeCount} repairable changes were sealed.",
                    NamedTextColor.RED,
                ),
            )
        state.activeSeason?.battleParticipants?.get(battle.id).orEmpty().forEach { participant ->
            server.getPlayer(participant.playerId.value)?.sendMessage(message)
        }
        logger.info(
            "Battle ${battle.id} closed with ${battle.outcome}; " +
                "journaled=${report.journaledChangeCount}, eligible=${report.eligibleChangeCount}",
        )
    }

    private fun fail(
        pending: PendingResolution,
        outcome: PaperBattleResolutionOutcome,
    ) {
        releaseChunk(pending.battleId)
        if (active?.pending === pending) {
            active = null
            resumeRepairWorldWork()
        }
        pending.complete(outcome)
        when (outcome) {
            is PaperBattleResolutionOutcome.Failed -> logger.log(
                Level.SEVERE,
                "Battle ${pending.battleId} resolution failed",
                outcome.failure,
            )
            is PaperBattleResolutionOutcome.Rejected -> logger.warning(
                "Battle ${pending.battleId} resolution rejected: ${outcome.description}",
            )
            is PaperBattleResolutionOutcome.Unavailable -> logger.warning(
                "Battle ${pending.battleId} remains RESOLVING: ${outcome.description}",
            )
            is PaperBattleResolutionOutcome.Completed -> Unit
        }
    }

    private fun ensureChunk(battleId: BattleId, position: BlockPosition3D): ChunkAccess {
        val world = world(position.worldId)
            ?: return ChunkAccess.Failed("World ${position.worldId} is unavailable")
        if (position.y !in world.minHeight until world.maxHeight) {
            return ChunkAccess.Failed(
                "Y ${position.y} is outside world ${position.worldId}",
            )
        }
        val key = ChunkKey(
            position.worldId,
            Math.floorDiv(position.x, CHUNK_WIDTH),
            Math.floorDiv(position.z, CHUNK_WIDTH),
        )
        heldChunk?.let { held ->
            if (held.battleId == battleId && held.key == key) return ChunkAccess.Ready
            releaseChunk()
        }
        if (pendingChunkLoad != null) return ChunkAccess.Waiting
        if (world.isChunkLoaded(key.x, key.z)) {
            val chunk = world.getChunkAt(key.x, key.z)
            return if (acquireChunk(battleId, key, chunk)) {
                ChunkAccess.Ready
            } else {
                ChunkAccess.Failed(
                    "Chunk ${key.x},${key.z} in ${key.worldId} could not be ticketed",
                )
            }
        }

        val pending = PendingChunkLoad(battleId, key)
        pendingChunkLoad = pending
        metrics.chunkLoads++
        world.getChunkAtAsync(key.x, key.z, false).whenComplete { chunk, failure ->
            dispatchToServer {
                if (closed || pendingChunkLoad !== pending) return@dispatchToServer
                pendingChunkLoad = null
                if (active?.pending?.battleId != battleId) return@dispatchToServer
                if (failure != null || chunk == null) {
                    chunkFailures[battleId] =
                        "Chunk ${key.x},${key.z} in ${key.worldId} could not be loaded"
                    return@dispatchToServer
                }
                if (!acquireChunk(battleId, key, chunk)) {
                    chunkFailures[battleId] =
                        "Chunk ${key.x},${key.z} in ${key.worldId} could not be ticketed"
                }
            }
        }
        return ChunkAccess.Waiting
    }

    private fun acquireChunk(battleId: BattleId, key: ChunkKey, chunk: Chunk): Boolean {
        val world = chunk.world
        val added = world.addPluginChunkTicket(key.x, key.z, plugin)
        val owned = added || plugin in world.getPluginChunkTickets(key.x, key.z)
        if (owned) heldChunk = HeldChunk(battleId, key, chunk)
        return owned
    }

    private fun releaseChunk(battleId: BattleId? = null) {
        val held = heldChunk ?: return
        if (battleId != null && held.battleId != battleId) return
        held.chunk.world.removePluginChunkTicket(held.key.x, held.key.z, plugin)
        heldChunk = null
    }

    private fun world(worldId: WorldId): World? =
        NamespacedKey.fromString(worldId.value)?.let(server::getWorld)

    private fun dispatchToServer(action: () -> Unit) {
        if (server.isPrimaryThread) {
            action()
        } else if (!closed && plugin.isEnabled) {
            server.scheduler.runTask(plugin, action)
        }
    }

    private fun suspendRepairWorldWork() {
        if (repairWorldWorkSuspended) return
        repairCoordinator.suspendForBattleResolution()
        landProtectionCoordinator.suspendForBattleResolution()
        repairWorldWorkSuspended = true
    }

    private fun resumeRepairWorldWork() {
        if (!repairWorldWorkSuspended) return
        repairCoordinator.resumeAfterBattleResolution()
        landProtectionCoordinator.resumeAfterBattleResolution()
        repairWorldWorkSuspended = false
    }

    private data class PendingResolution(
        val battleId: BattleId,
        var requestedOutcome: BattleOutcome?,
        val completions: MutableList<(PaperBattleResolutionOutcome) -> Unit> = mutableListOf(),
    ) {
        fun complete(outcome: PaperBattleResolutionOutcome) {
            completions.toList().forEach { it(outcome) }
            completions.clear()
        }
    }

    private data class ActiveResolution(
        val pending: PendingResolution,
        val battle: Battle,
        val orderedChanges: List<BattleBlockChange>,
        val observations: MutableList<FinalBlockObservation> = mutableListOf(),
        var index: Int = 0,
    )

    private data class ChunkKey(val worldId: WorldId, val x: Int, val z: Int)
    private data class HeldChunk(val battleId: BattleId, val key: ChunkKey, val chunk: Chunk)
    private data class PendingChunkLoad(val battleId: BattleId, val key: ChunkKey)

    private sealed interface ChunkAccess {
        data object Ready : ChunkAccess
        data object Waiting : ChunkAccess
        data class Failed(val description: String) : ChunkAccess
    }

    private data class MutableResolutionMetrics(
        var observed: Long = 0,
        var sealed: Long = 0,
        var closed: Long = 0,
        var sealOnly: Long = 0,
        var chunkLoads: Long = 0,
        var unavailable: Long = 0,
    )

    private companion object {
        const val CHUNK_WIDTH = 16
        const val MAX_PENDING_RESOLUTIONS = 64
        val RESOLUTION_ORDER = compareBy<BattleBlockChange>(
            { it.position.worldId.value },
            { Math.floorDiv(it.position.x, CHUNK_WIDTH) },
            { Math.floorDiv(it.position.z, CHUNK_WIDTH) },
            { it.position.y },
            { it.position.x },
            { it.position.z },
            { it.recordedAt },
            { it.id.toString() },
        )
    }
}

data class PaperBattleResolutionResult(
    val battle: Battle,
    val report: BattleDamageReport,
    val closed: Boolean,
)

sealed interface PaperBattleResolutionOutcome {
    data class Completed(val value: PaperBattleResolutionResult) : PaperBattleResolutionOutcome
    data class Rejected(val description: String) : PaperBattleResolutionOutcome
    data class Unavailable(val description: String) : PaperBattleResolutionOutcome
    data class Failed(val failure: Throwable) : PaperBattleResolutionOutcome
}
