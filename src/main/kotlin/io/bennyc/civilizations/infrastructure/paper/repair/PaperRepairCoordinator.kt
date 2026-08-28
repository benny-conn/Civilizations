package io.bennyc.civilizations.infrastructure.paper.repair

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.repair.CreateRepairJobRequest
import io.bennyc.civilizations.application.repair.CreatedRepairJob
import io.bennyc.civilizations.application.repair.CurrentRepairObservation
import io.bennyc.civilizations.application.repair.QuoteRepairRequest
import io.bennyc.civilizations.application.repair.RecordRepairWorkBatch
import io.bennyc.civilizations.application.repair.RepairAssessment
import io.bennyc.civilizations.application.repair.RepairAssessmentBasis
import io.bennyc.civilizations.application.repair.RepairQuote
import io.bennyc.civilizations.application.repair.RepairWorkBatch
import io.bennyc.civilizations.application.repair.RepairWorkItem
import io.bennyc.civilizations.application.repair.RepairWorkResult
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.repair.RepairFundingMode
import io.bennyc.civilizations.domain.repair.RepairJob
import io.bennyc.civilizations.domain.repair.RepairJobId
import io.bennyc.civilizations.domain.repair.RepairJobItemStatus
import io.bennyc.civilizations.domain.repair.RepairJobStatus
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.infrastructure.paper.RepairRunnerRules
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Owns the Paper side of repairs. SQL remains on the runtime worker; observations and
 * compare-before-write mutations happen only on the server thread and within global budgets.
 */
class PaperRepairCoordinator(
    private val plugin: Plugin,
    private val runtime: CivilizationsRuntime,
    private val server: Server,
    private val rules: RepairRunnerRules,
    private val logger: Logger,
) : AutoCloseable {
    private val scanQueue = ArrayDeque<PendingScan>()
    private val runnerQueue = LinkedHashSet<RepairJobId>()
    private val suppressedJobs = mutableSetOf<RepairJobId>()
    private var activeScan: ActiveScan? = null
    private var pendingScanBases = 0
    private var activeJobId: RepairJobId? = null
    private var activeBatch: RepairWorkBatch? = null
    private var storageBusy = false
    private var battleResolutionSuspended = false
    private var closed = false
    private var heldChunk: HeldChunk? = null
    private var pendingChunkLoad: PendingChunkLoad? = null
    private val chunkFailures = mutableMapOf<Any, String>()
    private val metrics = MutableRepairMetrics()
    private val task: BukkitTask = server.scheduler.runTaskTimer(
        plugin,
        Runnable(::tick),
        1L,
        1L,
    )

    val configuredBlocksPerTick: Int
        get() = rules.blocksPerTick

    val configuredBlocksPerSecond: Int
        get() = Math.multiplyExact(rules.blocksPerTick, SERVER_TICKS_PER_SECOND)

    /**
     * Serializes plugin chunk-ticket ownership with the battle-resolution scanner.
     * Existing repair work stays durable and simply waits while final damage is observed.
     */
    internal fun suspendForBattleResolution() {
        check(server.isPrimaryThread) { "Paper world work must be suspended on the server thread" }
        if (battleResolutionSuspended) return
        battleResolutionSuspended = true
        pendingChunkLoad = null
        releaseChunk()
    }

    internal fun resumeAfterBattleResolution() {
        check(server.isPrimaryThread) { "Paper world work must resume on the server thread" }
        battleResolutionSuspended = false
    }

    fun status(
        battleId: BattleId,
        civilizationId: CivilizationId,
        completion: (PaperRepairOutcome<PaperRepairStatus>) -> Unit,
    ) {
        beginScan(battleId, civilizationId) { scan ->
            when (scan) {
                is PaperRepairOutcome.Completed -> loadStatus(scan.value, completion)
                is PaperRepairOutcome.Rejected -> completion(scan)
                is PaperRepairOutcome.Unavailable -> completion(scan)
                is PaperRepairOutcome.Failed -> completion(scan)
            }
        }
    }

    fun startOrdinary(
        battleId: BattleId,
        civilizationId: CivilizationId,
        playerId: PlayerId,
        targetCompletionBasisPoints: Int,
        completion: (PaperRepairOutcome<CreatedRepairJob>) -> Unit,
    ) = startRepair(
        battleId = battleId,
        civilizationId = civilizationId,
        playerId = playerId,
        fundingMode = RepairFundingMode.ORDINARY,
        targetCompletionBasisPoints = targetCompletionBasisPoints,
        completion = completion,
    )

    fun startSponsored(
        battleId: BattleId,
        civilizationId: CivilizationId,
        adminPlayerId: PlayerId?,
        targetCompletionBasisPoints: Int,
        completion: (PaperRepairOutcome<CreatedRepairJob>) -> Unit,
    ) = startRepair(
        battleId = battleId,
        civilizationId = civilizationId,
        playerId = adminPlayerId,
        fundingMode = RepairFundingMode.ADMIN_SPONSORED,
        targetCompletionBasisPoints = targetCompletionBasisPoints,
        completion = completion,
    )

    fun inspect(
        jobId: RepairJobId,
        completion: (PaperRepairOutcome<RepairJob>) -> Unit,
    ) = submitRepair({ find(jobId) }, completion)

    fun listForBattle(
        battleId: BattleId,
        completion: (PaperRepairOutcome<List<RepairJob>>) -> Unit,
    ) = submitRepair(
        operation = { ApplicationResult.Unchanged(listForBattle(battleId)) },
        completion = completion,
    )

    fun resume(
        jobId: RepairJobId,
        completion: (PaperRepairOutcome<RepairJob>) -> Unit,
    ) {
        suppressedJobs.remove(jobId)
        submitRepair({ startExecution(jobId) }) { outcome ->
            if (outcome is PaperRepairOutcome.Completed) {
                enqueue(outcome.value.id)
            }
            completion(outcome)
        }
    }

    fun pause(
        jobId: RepairJobId,
        completion: (PaperRepairOutcome<RepairJob>) -> Unit,
    ) {
        suppress(jobId)
        submitRepair({ pause(jobId) }, completion)
    }

    fun cancel(
        jobId: RepairJobId,
        completion: (PaperRepairOutcome<RepairJob>) -> Unit,
    ) {
        suppress(jobId)
        submitRepair({ cancel(jobId) }, completion)
    }

    fun metricsSummary(): String =
        "queuedJobs=${runnerQueue.size}, queuedScans=${scanQueue.size}, " +
            "restored=${metrics.restored}, alreadyRestored=${metrics.alreadyRestored}, " +
            "conflicts=${metrics.conflicts}, failedItems=${metrics.failedItems}, " +
            "collisionDeferrals=${metrics.collisionDeferrals}, " +
            "chunkLoads=${metrics.chunkLoads}, paused=${metrics.pausedJobs}, " +
            "blocksPerTick=${rules.blocksPerTick}, " +
            "assessmentBlocksPerTick=${rules.assessmentBlocksPerTick}"

    override fun close() {
        if (closed) return
        closed = true
        task.cancel()
        scanQueue.forEach { pending ->
            pending.completion(PaperRepairOutcome.Unavailable("The server is stopping"))
        }
        scanQueue.clear()
        activeScan?.pending?.completion(
            PaperRepairOutcome.Unavailable("The server is stopping"),
        )
        activeScan = null
        releaseChunk()
        server.worlds.forEach { world -> world.removePluginChunkTickets(plugin) }
    }

    private fun startRepair(
        battleId: BattleId,
        civilizationId: CivilizationId,
        playerId: PlayerId?,
        fundingMode: RepairFundingMode,
        targetCompletionBasisPoints: Int,
        completion: (PaperRepairOutcome<CreatedRepairJob>) -> Unit,
    ) {
        beginScan(battleId, civilizationId) { scan ->
            val observations = when (scan) {
                is PaperRepairOutcome.Completed -> scan.value.observations
                is PaperRepairOutcome.Rejected -> return@beginScan completion(scan)
                is PaperRepairOutcome.Unavailable -> return@beginScan completion(scan)
                is PaperRepairOutcome.Failed -> return@beginScan completion(scan)
            }
            runtime.submitMutation(
                operation = {
                    repairs.create(
                        CreateRepairJobRequest(
                            battleId = battleId,
                            civilizationId = civilizationId,
                            initiatedByPlayerId = playerId,
                            fundingMode = fundingMode,
                            targetCompletionBasisPoints = targetCompletionBasisPoints,
                            observations = observations,
                            idempotencyKey = "paper-repair:${UUID.randomUUID()}",
                        ),
                    )
                },
            ) { outcome ->
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> startCreated(result.value, completion)
                        is ApplicationResult.Unchanged -> startCreated(result.value, completion)
                        is ApplicationResult.Rejected -> completion(
                            PaperRepairOutcome.Rejected(result.failure.description),
                        )
                    }
                    is RuntimeMutationOutcome.NotReady -> completion(
                        PaperRepairOutcome.Unavailable("Civilizations is not ready"),
                    )
                    is RuntimeMutationOutcome.Failed -> completion(
                        PaperRepairOutcome.Failed(outcome.failure),
                    )
                }
            }
        }
    }

    private fun beginScan(
        battleId: BattleId,
        civilizationId: CivilizationId,
        completion: (PaperRepairOutcome<CompletedScan>) -> Unit,
    ) {
        if (closed) {
            return completion(PaperRepairOutcome.Unavailable("The repair runner is stopped"))
        }
        if (pendingScanBases + scanQueue.size + (if (activeScan == null) 0 else 1) >=
            MAX_PENDING_SCANS
        ) {
            metrics.rejectedScans++
            return completion(
                PaperRepairOutcome.Unavailable("Repair scans are busy; try again shortly"),
            )
        }
        pendingScanBases++
        submitRepair({ loadAssessmentBasis(battleId, civilizationId) }) { outcome ->
            pendingScanBases--
            if (closed) {
                return@submitRepair completion(
                    PaperRepairOutcome.Unavailable("The repair runner is stopped"),
                )
            }
            when (outcome) {
                is PaperRepairOutcome.Completed -> {
                    val ordered = outcome.value.eligibleChanges.sortedWith(
                        compareBy(
                            { it.journalEntry.position.worldId.value },
                            { Math.floorDiv(it.journalEntry.position.x, CHUNK_WIDTH) },
                            { Math.floorDiv(it.journalEntry.position.z, CHUNK_WIDTH) },
                            { it.journalEntry.position.y },
                            { it.journalEntry.position.x },
                            { it.journalEntry.position.z },
                        ),
                    )
                    scanQueue.addLast(
                        PendingScan(outcome.value, ordered, completion),
                    )
                }
                is PaperRepairOutcome.Rejected -> completion(outcome)
                is PaperRepairOutcome.Unavailable -> completion(outcome)
                is PaperRepairOutcome.Failed -> completion(outcome)
            }
        }
    }

    private fun loadStatus(
        scan: CompletedScan,
        completion: (PaperRepairOutcome<PaperRepairStatus>) -> Unit,
    ) {
        submitRepair(
            operation = {
                val assessment = when (val result = assess(scan.basis, scan.observations)) {
                    is ApplicationResult.Applied -> result.value
                    is ApplicationResult.Rejected -> return@submitRepair result
                    is ApplicationResult.Unchanged -> error("Repair assessment cannot be unchanged")
                }
                val quote = quote(
                    QuoteRepairRequest(
                        battleId = scan.basis.battle.id,
                        civilizationId = scan.basis.civilizationId,
                        targetCompletionBasisPoints = 10_000,
                        observations = scan.observations,
                    ),
                )
                val jobs = listForBattle(scan.basis.battle.id)
                    .filter { it.civilizationId == scan.basis.civilizationId }
                ApplicationResult.Applied(PaperRepairStatus(assessment, quote, jobs))
            },
            completion = completion,
        )
    }

    private fun tick() {
        if (closed || battleResolutionSuspended) return
        try {
            if (activeScan != null || scanQueue.isNotEmpty()) {
                tickScan()
            } else {
                tickRunner()
            }
        } catch (failure: Throwable) {
            logger.log(Level.SEVERE, "Unexpected Paper repair runner failure", failure)
            val scan = activeScan
            if (scan != null) {
                finishScan(scan, PaperRepairOutcome.Failed(failure))
            } else {
                activeJobId?.let { jobId ->
                    pauseRunnerJob(
                        jobId,
                        activeBatch?.job,
                        "Unexpected runner error: ${failure.message ?: failure::class.simpleName}",
                    )
                }
            }
        }
    }

    private fun tickScan() {
        val scan = activeScan ?: scanQueue.removeFirstOrNull()?.let(::ActiveScan)?.also {
            activeScan = it
        } ?: return
        chunkFailures.remove(scan.token)?.let { failure ->
            return finishScan(scan, PaperRepairOutcome.Unavailable(failure))
        }

        var remaining = rules.assessmentBlocksPerTick
        while (remaining > 0 && scan.index < scan.pending.orderedChanges.size) {
            val change = scan.pending.orderedChanges[scan.index]
            val position = change.journalEntry.position
            when (val access = ensureChunk(scan.token, position)) {
                ChunkAccess.Ready -> Unit
                ChunkAccess.Waiting -> return
                is ChunkAccess.Failed -> return finishScan(
                    scan,
                    PaperRepairOutcome.Unavailable(access.description),
                )
            }
            val world = world(position.worldId)
                ?: return finishScan(
                    scan,
                    PaperRepairOutcome.Unavailable("World ${position.worldId} is unavailable"),
                )
            val current = world.getBlockAt(position.x, position.y, position.z)
                .blockData
                .getAsString(false)
            scan.observations += CurrentRepairObservation(
                change.journalEntry.id,
                SimpleBlockSnapshot(current),
            )
            scan.index++
            remaining--
            metrics.observed++
        }
        if (scan.index == scan.pending.orderedChanges.size) {
            finishScan(
                scan,
                PaperRepairOutcome.Completed(
                    CompletedScan(scan.pending.basis, scan.observations.toList()),
                ),
            )
        }
    }

    private fun finishScan(
        scan: ActiveScan,
        outcome: PaperRepairOutcome<CompletedScan>,
    ) {
        releaseChunk(scan.token)
        activeScan = null
        scan.pending.completion(outcome)
    }

    private fun tickRunner() {
        if (storageBusy) return
        val jobId = activeJobId ?: runnerQueue.firstOrNull()?.also { activeJobId = it } ?: return
        if (jobId in suppressedJobs) {
            removeRunnerJob(jobId)
            return
        }
        val batch = activeBatch
        if (batch == null) {
            storageBusy = true
            runtime.submitRepairOperation(
                operation = { loadWorkBatch(jobId, rules.blocksPerTick) },
            ) { outcome ->
                storageBusy = false
                if (jobId in suppressedJobs) return@submitRepairOperation
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> {
                            if (result.value.items.isEmpty()) {
                                failRunnerJob(jobId, "Repair cursor has no pending work")
                            } else {
                                activeBatch = result.value
                            }
                        }
                        is ApplicationResult.Unchanged -> activeBatch = result.value
                        is ApplicationResult.Rejected -> {
                            logger.warning(result.failure.description)
                            removeRunnerJob(jobId)
                        }
                    }
                    is RuntimeMutationOutcome.NotReady -> removeRunnerJob(jobId)
                    is RuntimeMutationOutcome.Failed -> removeRunnerJob(jobId)
                }
            }
            return
        }

        chunkFailures.remove(jobId)?.let { failure ->
            pauseRunnerJob(jobId, batch.job, failure)
            return
        }
        val results = mutableListOf<RepairWorkResult>()
        for (item in batch.items) {
            val position = item.change.journalEntry.position
            when (val access = ensureChunk(jobId, position)) {
                ChunkAccess.Ready -> Unit
                ChunkAccess.Waiting -> break
                is ChunkAccess.Failed -> {
                    if (results.isEmpty()) {
                        pauseRunnerJob(jobId, batch.job, access.description)
                        return
                    }
                    break
                }
            }
            when (val execution = execute(item)) {
                RepairExecution.DeferredCollision -> {
                    metrics.collisionDeferrals++
                    break
                }
                is RepairExecution.Unavailable -> {
                    if (results.isEmpty()) {
                        pauseRunnerJob(jobId, batch.job, execution.description)
                        return
                    }
                    break
                }
                is RepairExecution.Completed -> results += execution.result
            }
        }
        if (results.isEmpty()) {
            return
        }
        // Keep the single lease across the cursor write. An async request for the next
        // chunk can complete inline; generic end-of-batch cleanup would release that new
        // lease before the next tick and create an acquire/release loop.
        storageBusy = true
        runtime.submitRepairOperation(
            operation = { recordWorkBatch(RecordRepairWorkBatch(jobId, results)) },
        ) { outcome ->
            storageBusy = false
            activeBatch = null
            if (jobId in suppressedJobs) return@submitRepairOperation
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> if (
                        result.value.status == RepairJobStatus.COMPLETED
                    ) {
                        removeRunnerJob(jobId)
                    }
                    is ApplicationResult.Unchanged -> if (
                        result.value.status == RepairJobStatus.COMPLETED
                    ) {
                        removeRunnerJob(jobId)
                    }
                    is ApplicationResult.Rejected -> {
                        logger.warning(result.failure.description)
                        removeRunnerJob(jobId)
                    }
                }
                is RuntimeMutationOutcome.NotReady -> removeRunnerJob(jobId)
                is RuntimeMutationOutcome.Failed -> removeRunnerJob(jobId)
            }
        }
    }

    private fun execute(item: RepairWorkItem): RepairExecution {
        val position = item.change.journalEntry.position
        val world = world(position.worldId)
            ?: return RepairExecution.Unavailable("World ${position.worldId} became unavailable")
        if (position.y !in world.minHeight until world.maxHeight) {
            return RepairExecution.Unavailable(
                "Y ${position.y} is outside world ${position.worldId}",
            )
        }
        val block = world.getBlockAt(position.x, position.y, position.z)
        val current = SimpleBlockSnapshot(block.blockData.getAsString(false))
        return when (val decision = RepairBlockDecision.decide(item, current)) {
            RepairBlockDecision.AlreadyRestored -> {
                metrics.alreadyRestored++
                RepairExecution.Completed(restored(item))
            }
            RepairBlockDecision.Conflict -> {
                metrics.conflicts++
                RepairExecution.Completed(
                    RepairWorkResult(
                        blockChangeId = item.item.blockChangeId,
                        ordinal = item.item.ordinal,
                        status = RepairJobItemStatus.SKIPPED_CONFLICT,
                    ),
                )
            }
            is RepairBlockDecision.Restore -> try {
                val restoredData = server.createBlockData(decision.original.blockData)
                if (restoredData.material.isSolid && collidesWithPlayer(world, position)) {
                    RepairExecution.DeferredCollision
                } else {
                    block.setBlockData(restoredData, false)
                    metrics.restored++
                    RepairExecution.Completed(restored(item))
                }
            } catch (failure: Throwable) {
                logger.log(Level.WARNING, "Repair item ${item.item.blockChangeId} failed", failure)
                RepairExecution.Completed(
                    failed(item, failure.message ?: failure::class.simpleName.orEmpty()),
                )
            }
        }
    }

    private fun collidesWithPlayer(world: World, position: BlockPosition3D): Boolean {
        val box = BoundingBox(
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            position.x + 1.0,
            position.y + 1.0,
            position.z + 1.0,
        )
        return world.players.any { player -> player.boundingBox.overlaps(box) }
    }

    private fun restored(item: RepairWorkItem) = RepairWorkResult(
        blockChangeId = item.item.blockChangeId,
        ordinal = item.item.ordinal,
        status = RepairJobItemStatus.RESTORED,
    )

    private fun failed(item: RepairWorkItem, message: String): RepairWorkResult {
        metrics.failedItems++
        return RepairWorkResult(
            blockChangeId = item.item.blockChangeId,
            ordinal = item.item.ordinal,
            status = RepairJobItemStatus.FAILED,
            failureMessage = message.take(512),
        )
    }

    private fun enqueue(jobId: RepairJobId) {
        suppressedJobs.remove(jobId)
        runnerQueue += jobId
    }

    private fun startCreated(
        created: CreatedRepairJob,
        completion: (PaperRepairOutcome<CreatedRepairJob>) -> Unit,
    ) {
        resume(created.job.id) { started ->
            when (started) {
                is PaperRepairOutcome.Completed ->
                    completion(PaperRepairOutcome.Completed(created))
                is PaperRepairOutcome.Rejected -> completion(started)
                is PaperRepairOutcome.Unavailable -> completion(started)
                is PaperRepairOutcome.Failed -> completion(started)
            }
        }
    }

    private fun suppress(jobId: RepairJobId) {
        suppressedJobs += jobId
        runnerQueue -= jobId
        if (activeJobId == jobId) {
            releaseChunk(jobId)
            activeJobId = null
            activeBatch = null
        }
    }

    private fun removeRunnerJob(jobId: RepairJobId) {
        runnerQueue -= jobId
        if (activeJobId == jobId) {
            releaseChunk(jobId)
            activeJobId = null
            activeBatch = null
        }
    }

    private fun pauseRunnerJob(jobId: RepairJobId, job: RepairJob?, reason: String) {
        metrics.pausedJobs++
        suppress(jobId)
        logger.warning(
            "Paused repair $jobId" +
                (job?.let { " at cursor ${it.nextItemOrdinal}" } ?: "") +
                ": $reason",
        )
        job?.initiatedByPlayerId?.let { initiator ->
            server.getPlayer(initiator.value)?.sendMessage(
                net.kyori.adventure.text.Component.text(
                    "[Civilizations] Repair $jobId paused: $reason",
                    net.kyori.adventure.text.format.NamedTextColor.RED,
                ),
            )
        }
        submitRepair({ pause(jobId) }) { outcome ->
            if (outcome is PaperRepairOutcome.Failed) {
                logger.log(Level.SEVERE, "Could not pause repair $jobId", outcome.failure)
            }
        }
    }

    private fun failRunnerJob(jobId: RepairJobId, reason: String) {
        suppress(jobId)
        submitRepair({ fail(jobId, reason) }) { outcome ->
            if (outcome is PaperRepairOutcome.Failed) {
                logger.log(Level.SEVERE, "Could not fail repair $jobId", outcome.failure)
            }
        }
    }

    private fun ensureChunk(owner: Any, position: BlockPosition3D): ChunkAccess {
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
            // A successfully acquired plugin ticket is this coordinator's lease authority.
            // Paper's async chunk handle may report a stale isLoaded value on a later tick.
            if (held.owner == owner && held.key == key) {
                return ChunkAccess.Ready
            }
            releaseChunk()
        }
        if (pendingChunkLoad != null) return ChunkAccess.Waiting
        if (world.isChunkLoaded(key.x, key.z)) {
            val chunk = world.getChunkAt(key.x, key.z)
            return if (acquireChunk(owner, key, chunk)) {
                ChunkAccess.Ready
            } else {
                ChunkAccess.Failed(
                    "Chunk ${key.x},${key.z} in ${key.worldId} could not be ticketed",
                )
            }
        }

        val pending = PendingChunkLoad(owner, key)
        pendingChunkLoad = pending
        metrics.chunkLoads++
        world.getChunkAtAsync(key.x, key.z, false).whenComplete { chunk, failure ->
            dispatchToServer {
                if (closed || pendingChunkLoad !== pending) return@dispatchToServer
                pendingChunkLoad = null
                if (!isChunkOwnerActive(owner)) return@dispatchToServer
                if (failure != null || chunk == null) {
                    chunkFailures[owner] =
                        "Chunk ${key.x},${key.z} in ${key.worldId} could not be loaded"
                    return@dispatchToServer
                }
                if (!acquireChunk(owner, key, chunk)) {
                    chunkFailures[owner] =
                        "Chunk ${key.x},${key.z} in ${key.worldId} could not be ticketed"
                }
            }
        }
        return ChunkAccess.Waiting
    }

    private fun releaseChunk(owner: Any? = null) {
        val held = heldChunk ?: return
        if (owner != null && held.owner != owner) return
        held.chunk.world.removePluginChunkTicket(held.key.x, held.key.z, plugin)
        heldChunk = null
    }

    private fun acquireChunk(owner: Any, key: ChunkKey, chunk: Chunk): Boolean {
        val world = chunk.world
        val added = world.addPluginChunkTicket(key.x, key.z, plugin)
        val owned = added || plugin in world.getPluginChunkTickets(key.x, key.z)
        if (owned) heldChunk = HeldChunk(owner, key, chunk)
        return owned
    }

    private fun isChunkOwnerActive(owner: Any): Boolean = when (owner) {
        is RepairJobId -> !battleResolutionSuspended &&
            owner in runnerQueue && owner !in suppressedJobs
        else -> !battleResolutionSuspended && activeScan?.token === owner
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

    private fun <T> submitRepair(
        operation: io.bennyc.civilizations.application.repair.RepairJobService.() ->
            ApplicationResult<T>,
        completion: (PaperRepairOutcome<T>) -> Unit,
    ) {
        runtime.submitRepairOperation(operation) { outcome ->
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied ->
                        completion(PaperRepairOutcome.Completed(result.value))
                    is ApplicationResult.Unchanged ->
                        completion(PaperRepairOutcome.Completed(result.value))
                    is ApplicationResult.Rejected ->
                        completion(PaperRepairOutcome.Rejected(result.failure.description))
                }
                is RuntimeMutationOutcome.NotReady -> completion(
                    PaperRepairOutcome.Unavailable("Civilizations is not ready"),
                )
                is RuntimeMutationOutcome.Failed ->
                    completion(PaperRepairOutcome.Failed(outcome.failure))
            }
        }
    }

    private data class PendingScan(
        val basis: RepairAssessmentBasis,
        val orderedChanges: List<io.bennyc.civilizations.domain.damage.ReportedBattleBlockChange>,
        val completion: (PaperRepairOutcome<CompletedScan>) -> Unit,
    )

    private data class ActiveScan(
        val pending: PendingScan,
        val observations: MutableList<CurrentRepairObservation> = mutableListOf(),
        var index: Int = 0,
        val token: Any = Any(),
    )

    private data class HeldChunk(
        val owner: Any,
        val key: ChunkKey,
        val chunk: Chunk,
    )

    private data class PendingChunkLoad(val owner: Any, val key: ChunkKey)

    private data class ChunkKey(val worldId: WorldId, val x: Int, val z: Int)

    private sealed interface ChunkAccess {
        data object Ready : ChunkAccess
        data object Waiting : ChunkAccess
        data class Failed(val description: String) : ChunkAccess
    }

    private sealed interface RepairExecution {
        data class Completed(val result: RepairWorkResult) : RepairExecution
        data class Unavailable(val description: String) : RepairExecution
        data object DeferredCollision : RepairExecution
    }

    private data class MutableRepairMetrics(
        var observed: Long = 0,
        var restored: Long = 0,
        var alreadyRestored: Long = 0,
        var conflicts: Long = 0,
        var failedItems: Long = 0,
        var collisionDeferrals: Long = 0,
        var chunkLoads: Long = 0,
        var pausedJobs: Long = 0,
        var rejectedScans: Long = 0,
    )

    private companion object {
        const val CHUNK_WIDTH = 16
        const val MAX_PENDING_SCANS = 8
        const val SERVER_TICKS_PER_SECOND = 20
    }
}

internal sealed interface RepairBlockDecision {
    data object AlreadyRestored : RepairBlockDecision
    data object Conflict : RepairBlockDecision
    data class Restore(val original: SimpleBlockSnapshot) : RepairBlockDecision

    companion object {
        fun decide(item: RepairWorkItem, current: SimpleBlockSnapshot): RepairBlockDecision =
            when (current) {
                item.change.journalEntry.originalState -> AlreadyRestored
                item.change.reportEntry.finalState -> Restore(item.change.journalEntry.originalState)
                else -> Conflict
            }
    }
}

data class CompletedScan(
    val basis: RepairAssessmentBasis,
    val observations: List<CurrentRepairObservation>,
)

data class PaperRepairStatus(
    val assessment: RepairAssessment,
    val quoteToFull: ApplicationResult<RepairQuote>,
    val jobs: List<RepairJob>,
)

sealed interface PaperRepairOutcome<out T> {
    data class Completed<T>(val value: T) : PaperRepairOutcome<T>
    data class Rejected(val description: String) : PaperRepairOutcome<Nothing>
    data class Unavailable(val description: String) : PaperRepairOutcome<Nothing>
    data class Failed(val failure: Throwable) : PaperRepairOutcome<Nothing>
}
