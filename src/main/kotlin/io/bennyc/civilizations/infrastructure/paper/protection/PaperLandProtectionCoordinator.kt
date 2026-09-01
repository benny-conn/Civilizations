package io.bennyc.civilizations.infrastructure.paper.protection

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.protection.ProtectionDamageObservation
import io.bennyc.civilizations.application.protection.ProtectionRepairAssessment
import io.bennyc.civilizations.application.protection.StartProtectionRepair
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.protection.ExposureDamageSiteId
import io.bennyc.civilizations.domain.protection.LandProtectionState
import io.bennyc.civilizations.domain.protection.ProtectionRepairItemStatus
import io.bennyc.civilizations.domain.protection.ProtectionRepairJob
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobId
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobItem
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobStatus
import io.bennyc.civilizations.domain.protection.ReportedExposureDamage
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.infrastructure.paper.RepairRunnerRules
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import org.bukkit.Chunk
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import java.util.ArrayDeque
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/** Bounded Paper observer/runner plus the periodic durable upkeep driver. */
class PaperLandProtectionCoordinator(
    private val plugin: Plugin,
    private val runtime: CivilizationsRuntime,
    private val server: Server,
    private val runnerRules: RepairRunnerRules,
    assessmentIntervalSeconds: Long,
    private val logger: Logger,
) : AutoCloseable {
    private val scans = ArrayDeque<QueuedScan>()
    private var activeScan: ActiveScan? = null
    private val jobs = ArrayDeque<ProtectionRepairJobId>()
    private var activeJob: ProtectionRepairJob? = null
    private var activeItem: ProtectionRepairJobItem? = null
    private var storageBusy = false
    private var battleResolutionSuspended = false
    @Volatile
    private var closed = false
    private var heldChunk: HeldChunk? = null
    private var pendingChunkLoad: PendingChunkLoad? = null
    private var chunkFailure: String? = null
    private var ordinaryWorldWorkBusy: () -> Boolean = { false }
    private val tickTask: BukkitTask = server.scheduler.runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    private val upkeepTask: BukkitTask = server.scheduler.runTaskTimer(
        plugin,
        Runnable(::assessUpkeep),
        1L,
        Math.multiplyExact(assessmentIntervalSeconds, 20L),
    )

    val isWorldWorkActive: Boolean
        get() = activeScan != null || activeJob != null || pendingChunkLoad != null

    fun setOrdinaryWorldWorkBusy(provider: () -> Boolean) {
        ordinaryWorldWorkBusy = provider
    }

    internal fun suspendForBattleResolution() {
        check(server.isPrimaryThread) { "Paper world work must be suspended on the server thread" }
        battleResolutionSuspended = true
        pendingChunkLoad = null
        releaseChunk()
    }

    internal fun resumeAfterBattleResolution() {
        check(server.isPrimaryThread) { "Paper world work must resume on the server thread" }
        battleResolutionSuspended = false
    }

    fun recover() {
        runtime.submitProtectionRepairOperation(
            operation = { listJobs(setOf(ProtectionRepairJobStatus.PENDING), 1_000) },
        ) { outcome ->
            if (outcome is RuntimeMutationOutcome.Completed) {
                val result = outcome.result
                val recovered = when (result) {
                    is ApplicationResult.Applied -> result.value
                    is ApplicationResult.Unchanged -> result.value
                    is ApplicationResult.Rejected -> emptyList()
                }
                recovered.forEach { enqueue(it.id) }
            }
        }
    }

    fun assessNow(
        civilizationId: CivilizationId,
        completion: (LandProtectionPaperOutcome<LandProtectionState>) -> Unit,
    ) {
        runtime.submitMutation(
            operation = { landProtection.assess(civilizationId) },
        ) { outcome -> completion(outcome.translate()) }
    }

    fun status(
        civilizationId: CivilizationId,
        completion: (LandProtectionPaperOutcome<ProtectionRepairAssessment>) -> Unit,
    ) = collectDamage(civilizationId) { outcome ->
        when (outcome) {
            is LandProtectionPaperOutcome.Completed -> scans += QueuedScan(
                civilizationId,
                outcome.value,
            ) { scan ->
                when (scan) {
                    is LandProtectionPaperOutcome.Completed ->
                        runtime.submitProtectionRepairOperation(
                            operation = { assess(civilizationId, scan.value) },
                        ) { result -> completion(result.translate()) }
                    is LandProtectionPaperOutcome.Rejected -> completion(scan)
                    is LandProtectionPaperOutcome.Unavailable -> completion(scan)
                    is LandProtectionPaperOutcome.Failed -> completion(scan)
                }
            }
            is LandProtectionPaperOutcome.Rejected -> completion(outcome)
            is LandProtectionPaperOutcome.Unavailable -> completion(outcome)
            is LandProtectionPaperOutcome.Failed -> completion(outcome)
        }
    }

    fun start(
        civilizationId: CivilizationId,
        playerId: PlayerId,
        targetBasisPoints: Int,
        completion: (LandProtectionPaperOutcome<ProtectionRepairJob>) -> Unit,
    ) = collectDamage(civilizationId) { outcome ->
        when (outcome) {
            is LandProtectionPaperOutcome.Completed -> scans += QueuedScan(
                civilizationId,
                outcome.value,
            ) { scan ->
                when (scan) {
                    is LandProtectionPaperOutcome.Completed -> runtime.submitMutation(
                        operation = {
                            protectionRepairs.start(
                                StartProtectionRepair(
                                    civilizationId = civilizationId,
                                    initiatedByPlayerId = playerId,
                                    targetCompletionBasisPoints = targetBasisPoints,
                                    observations = scan.value,
                                    idempotencyKey = "protection-repair:${UUID.randomUUID()}",
                                ),
                            )
                        },
                    ) { result ->
                        val translated = result.translate<ProtectionRepairJob>()
                        if (translated is LandProtectionPaperOutcome.Completed) {
                            enqueue(translated.value.id)
                        }
                        completion(translated)
                    }
                    is LandProtectionPaperOutcome.Rejected -> completion(scan)
                    is LandProtectionPaperOutcome.Unavailable -> completion(scan)
                    is LandProtectionPaperOutcome.Failed -> completion(scan)
                }
            }
            is LandProtectionPaperOutcome.Rejected -> completion(outcome)
            is LandProtectionPaperOutcome.Unavailable -> completion(outcome)
            is LandProtectionPaperOutcome.Failed -> completion(outcome)
        }
    }

    fun resume(
        jobId: ProtectionRepairJobId,
        civilizationId: CivilizationId,
        completion: (LandProtectionPaperOutcome<ProtectionRepairJob>) -> Unit,
    ) {
        runtime.submitProtectionRepairOperation(operation = {
            val job = findJob(jobId)
                ?: return@submitProtectionRepairOperation ApplicationResult.Rejected(
                    io.bennyc.civilizations.application.protection.ProtectionRepairJobNotFound(jobId),
                )
            if (job.civilizationId != civilizationId) {
                return@submitProtectionRepairOperation ApplicationResult.Rejected(
                    io.bennyc.civilizations.application.protection.ProtectionRepairAuthorityRequired,
                )
            }
            if (job.status != ProtectionRepairJobStatus.PAUSED) {
                return@submitProtectionRepairOperation ApplicationResult.Rejected(
                    io.bennyc.civilizations.application.protection.ProtectionRepairInvalidTransition(
                        job.status,
                    ),
                )
            }
            ApplicationResult.Unchanged(job)
        }) { outcome ->
            val translated = outcome.translate<ProtectionRepairJob>()
            if (translated is LandProtectionPaperOutcome.Completed) enqueue(jobId)
            completion(translated)
        }
    }

    private fun collectDamage(
        civilizationId: CivilizationId,
        completion: (LandProtectionPaperOutcome<List<ReportedExposureDamage>>) -> Unit,
    ) {
        val collected = mutableListOf<ReportedExposureDamage>()
        fun page(after: ExposureDamageSiteId?) {
            runtime.submitProtectionRepairOperation(
                operation = {
                    ApplicationResult.Applied(listDamage(civilizationId, after, PAGE_SIZE))
                },
            ) { outcome ->
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> {
                            collected += result.value
                            if (result.value.size == PAGE_SIZE) page(result.value.last().site.id)
                            else completion(LandProtectionPaperOutcome.Completed(collected))
                        }
                        is ApplicationResult.Unchanged -> completion(
                            LandProtectionPaperOutcome.Completed(result.value),
                        )
                        is ApplicationResult.Rejected -> completion(
                            LandProtectionPaperOutcome.Rejected(result.failure.description),
                        )
                    }
                    is RuntimeMutationOutcome.NotReady -> completion(
                        LandProtectionPaperOutcome.Unavailable("Civilizations is not ready"),
                    )
                    is RuntimeMutationOutcome.Failed -> completion(
                        LandProtectionPaperOutcome.Failed(outcome.failure),
                    )
                }
            }
        }
        page(null)
    }

    private fun assessUpkeep() {
        if (closed) return
        val seasonId = ((runtime.state as? CivilizationsRuntimeState.Ready)
            ?.activeSeason?.season?.id) ?: return
        runtime.submitMutation(operation = { landProtection.assessAll(seasonId) }) { outcome ->
            if (outcome is RuntimeMutationOutcome.Failed) {
                logger.log(Level.SEVERE, "Land protection assessment failed", outcome.failure)
            }
        }
    }

    private fun tick() {
        if (closed || battleResolutionSuspended || ordinaryWorldWorkBusy()) return
        try {
            if (activeScan != null || scans.isNotEmpty()) tickScan() else tickJob()
        } catch (failure: Throwable) {
            logger.log(Level.SEVERE, "Land protection world work failed", failure)
        }
    }

    private fun tickScan() {
        val scan = activeScan ?: run {
            val queued = if (scans.isEmpty()) null else scans.removeFirst()
            queued?.let(::ActiveScan)?.also { activeScan = it }
        } ?: return
        chunkFailure?.let { failure ->
            chunkFailure = null
            finishScan(scan, LandProtectionPaperOutcome.Unavailable(failure))
            return
        }
        var remaining = runnerRules.assessmentBlocksPerTick
        while (remaining-- > 0 && scan.index < scan.queued.damage.size) {
            val damage = scan.queued.damage[scan.index]
            when (val access = ensureChunk(scan, damage.site.position)) {
                ChunkAccess.Ready -> Unit
                ChunkAccess.Waiting -> return
                is ChunkAccess.Failed -> {
                    finishScan(scan, LandProtectionPaperOutcome.Unavailable(access.description))
                    return
                }
            }
            val block = world(damage.site.position.worldId)?.getBlockAt(
                damage.site.position.x,
                damage.site.position.y,
                damage.site.position.z,
            ) ?: run {
                finishScan(scan, LandProtectionPaperOutcome.Unavailable("World unavailable"))
                return
            }
            scan.observations += ProtectionDamageObservation(
                damage.site.id,
                SimpleBlockSnapshot(block.blockData.getAsString(false)),
            )
            scan.index++
        }
        if (scan.index == scan.queued.damage.size) {
            finishScan(scan, LandProtectionPaperOutcome.Completed(scan.observations))
        }
    }

    private fun finishScan(
        scan: ActiveScan,
        outcome: LandProtectionPaperOutcome<List<ProtectionDamageObservation>>,
    ) {
        releaseChunk()
        pendingChunkLoad = pendingChunkLoad?.takeUnless { it.owner === scan }
        activeScan = null
        scan.queued.completion(outcome)
    }

    private fun enqueue(jobId: ProtectionRepairJobId) {
        if (activeJob?.id != jobId && jobId !in jobs) jobs += jobId
    }

    private fun tickJob() {
        if (storageBusy) return
        val job = activeJob
        if (job == null) {
            val id = if (jobs.isEmpty()) return else jobs.removeFirst()
            storageBusy = true
            runtime.submitProtectionRepairOperation(operation = {
                val current = findJob(id)
                    ?: return@submitProtectionRepairOperation ApplicationResult.Rejected(
                        io.bennyc.civilizations.application.protection.ProtectionRepairJobNotFound(id),
                    )
                if (current.status == ProtectionRepairJobStatus.RUNNING) {
                    ApplicationResult.Unchanged(current)
                } else {
                    begin(id)
                }
            }) { outcome ->
                storageBusy = false
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> activeJob = result.value
                        is ApplicationResult.Unchanged -> activeJob = result.value
                        is ApplicationResult.Rejected -> logger.warning(result.failure.description)
                    }
                    else -> Unit
                }
            }
            return
        }
        val hasOpenBattle = (runtime.state as? CivilizationsRuntimeState.Ready)
            ?.activeSeason?.battles.orEmpty().any { battle ->
                battle.status in setOf(BattleStatus.ACTIVE, BattleStatus.RESOLVING) &&
                    job.civilizationId in setOf(
                        battle.attackingCivilizationId,
                        battle.defendingCivilizationId,
                    )
            }
        if (hasOpenBattle) return
        if (job.status == ProtectionRepairJobStatus.COMPLETED) {
            activeJob = null
            activeItem = null
            releaseChunk()
            return
        }
        val item = activeItem
        if (item == null) {
            storageBusy = true
            runtime.submitProtectionRepairOperation(
                operation = {
                    val current = findJob(job.id)
                        ?: return@submitProtectionRepairOperation ApplicationResult.Rejected(
                            io.bennyc.civilizations.application.protection.ProtectionRepairJobNotFound(job.id),
                        )
                    val next = listItems(job.id, current.nextItemOrdinal - 1, 1).singleOrNull()
                    if (next == null) ApplicationResult.Rejected(
                        io.bennyc.civilizations.application.protection.ProtectionRepairCursorConflict,
                    ) else ApplicationResult.Applied(current to next)
                },
            ) { outcome ->
                storageBusy = false
                if (outcome is RuntimeMutationOutcome.Completed &&
                    outcome.result is ApplicationResult.Applied
                ) {
                    val pair = outcome.result.value
                    activeJob = pair.first
                    activeItem = pair.second
                }
            }
            return
        }
        chunkFailure?.let { failure ->
            chunkFailure = null
            pauseJob(job, failure)
            return
        }
        when (val access = ensureChunk(job.id, item.position)) {
            ChunkAccess.Ready -> Unit
            ChunkAccess.Waiting -> return
            is ChunkAccess.Failed -> return pauseJob(job, access.description)
        }
        val world = world(item.position.worldId) ?: return pauseJob(job, "World unavailable")
        val block = world.getBlockAt(item.position.x, item.position.y, item.position.z)
        val current = SimpleBlockSnapshot(block.blockData.getAsString(false))
        val result = when {
            current == item.restoreState -> ProtectionRepairItemStatus.RESTORED
            current != item.expectedState -> ProtectionRepairItemStatus.SKIPPED_CONFLICT
            server.createBlockData(item.restoreState.blockData).material.isSolid &&
                collidesWithPlayer(world, item.position) -> return
            else -> try {
                block.setBlockData(server.createBlockData(item.restoreState.blockData), false)
                ProtectionRepairItemStatus.RESTORED
            } catch (failure: Throwable) {
                logger.log(Level.WARNING, "Protection repair item failed", failure)
                ProtectionRepairItemStatus.FAILED
            }
        }
        storageBusy = true
        runtime.submitProtectionRepairOperation(
            operation = { recordItem(job.id, item.ordinal, result) },
        ) { outcome ->
            storageBusy = false
            activeItem = null
            if (outcome is RuntimeMutationOutcome.Completed) {
                when (val saved = outcome.result) {
                    is ApplicationResult.Applied -> activeJob = saved.value
                    is ApplicationResult.Unchanged -> activeJob = saved.value
                    is ApplicationResult.Rejected -> pauseJob(job, saved.failure.description)
                }
            }
        }
    }

    private fun pauseJob(job: ProtectionRepairJob, reason: String) {
        logger.warning("Pausing protection repair ${job.id}: $reason")
        storageBusy = true
        pendingChunkLoad = pendingChunkLoad?.takeUnless { it.owner == job.id }
        runtime.submitProtectionRepairOperation(operation = { pause(job.id) }) {
            storageBusy = false
            activeJob = null
            activeItem = null
            releaseChunk()
        }
    }

    private fun ensureChunk(owner: Any, position: BlockPosition3D): ChunkAccess {
        val world = world(position.worldId)
            ?: return ChunkAccess.Failed("World ${position.worldId} is unavailable")
        if (position.y !in world.minHeight until world.maxHeight) {
            return ChunkAccess.Failed("Y ${position.y} is outside world ${position.worldId}")
        }
        val key = ChunkKey(world.uid, Math.floorDiv(position.x, 16), Math.floorDiv(position.z, 16))
        if (heldChunk?.let { it.owner == owner && it.key == key } == true) {
            return ChunkAccess.Ready
        }
        releaseChunk()
        if (pendingChunkLoad != null) return ChunkAccess.Waiting
        if (world.isChunkLoaded(key.x, key.z)) {
            return if (hold(owner, world.getChunkAt(key.x, key.z), key)) {
                ChunkAccess.Ready
            } else {
                ChunkAccess.Failed("Chunk ${key.x},${key.z} is unavailable")
            }
        }
        val pending = PendingChunkLoad(owner, key)
        pendingChunkLoad = pending
        world.getChunkAtAsync(key.x, key.z, false).whenComplete { chunk, failure ->
            if (closed) return@whenComplete
            runCatching {
                server.scheduler.runTask(plugin, Runnable {
                    if (closed || pendingChunkLoad !== pending) return@Runnable
                    pendingChunkLoad = null
                    if (battleResolutionSuspended || !isOwnerActive(owner)) return@Runnable
                    if (failure != null || chunk == null || !hold(owner, chunk, key)) {
                        chunkFailure = failure?.message ?: "Chunk ${key.x},${key.z} is unavailable"
                    }
                })
            }.onFailure {
                if (!closed) logger.log(Level.WARNING, "Could not finish protection chunk load", it)
            }
        }
        return ChunkAccess.Waiting
    }

    private fun isOwnerActive(owner: Any): Boolean = when (owner) {
        is ActiveScan -> activeScan === owner
        is ProtectionRepairJobId -> activeJob?.id == owner && activeScan == null && scans.isEmpty()
        else -> false
    }

    private fun hold(owner: Any, chunk: Chunk, key: ChunkKey): Boolean {
        if (!chunk.world.addPluginChunkTicket(key.x, key.z, plugin)) return false
        heldChunk = HeldChunk(owner, key, chunk)
        return true
    }

    private fun releaseChunk() {
        heldChunk?.let { held ->
            held.chunk.world.removePluginChunkTicket(held.key.x, held.key.z, plugin)
        }
        heldChunk = null
    }

    private fun world(id: WorldId): World? =
        NamespacedKey.fromString(id.value)?.let(server::getWorld)

    private fun collidesWithPlayer(world: World, position: BlockPosition3D): Boolean {
        val bounds = BoundingBox(
            position.x.toDouble(), position.y.toDouble(), position.z.toDouble(),
            position.x + 1.0, position.y + 1.0, position.z + 1.0,
        )
        return world.players.any { it.boundingBox.overlaps(bounds) }
    }

    override fun close() {
        if (closed) return
        closed = true
        tickTask.cancel()
        upkeepTask.cancel()
        pendingChunkLoad = null
        releaseChunk()
        val unavailable = LandProtectionPaperOutcome.Unavailable("The server is stopping")
        activeScan?.queued?.completion(unavailable)
        scans.forEach { it.completion(unavailable) }
        scans.clear()
    }

    private data class QueuedScan(
        val civilizationId: CivilizationId,
        val damage: List<ReportedExposureDamage>,
        val completion: (LandProtectionPaperOutcome<List<ProtectionDamageObservation>>) -> Unit,
    )

    private data class ActiveScan(
        val queued: QueuedScan,
        var index: Int = 0,
        val observations: MutableList<ProtectionDamageObservation> = mutableListOf(),
    )

    private data class ChunkKey(val worldId: UUID, val x: Int, val z: Int)
    private data class PendingChunkLoad(val owner: Any, val key: ChunkKey)
    private data class HeldChunk(val owner: Any, val key: ChunkKey, val chunk: Chunk)
    private sealed interface ChunkAccess {
        data object Ready : ChunkAccess
        data object Waiting : ChunkAccess
        data class Failed(val description: String) : ChunkAccess
    }

    private companion object {
        const val PAGE_SIZE = 1_000
    }
}

sealed interface LandProtectionPaperOutcome<out T> {
    data class Completed<T>(val value: T) : LandProtectionPaperOutcome<T>
    data class Rejected(val description: String) : LandProtectionPaperOutcome<Nothing>
    data class Unavailable(val description: String) : LandProtectionPaperOutcome<Nothing>
    data class Failed(val failure: Throwable) : LandProtectionPaperOutcome<Nothing>
}

private fun <T> RuntimeMutationOutcome<T>.translate(): LandProtectionPaperOutcome<T> = when (this) {
    is RuntimeMutationOutcome.Completed -> when (val result = result) {
        is ApplicationResult.Applied -> LandProtectionPaperOutcome.Completed(result.value)
        is ApplicationResult.Unchanged -> LandProtectionPaperOutcome.Completed(result.value)
        is ApplicationResult.Rejected -> LandProtectionPaperOutcome.Rejected(result.failure.description)
    }
    is RuntimeMutationOutcome.NotReady -> LandProtectionPaperOutcome.Unavailable("Civilizations is not ready")
    is RuntimeMutationOutcome.Failed -> LandProtectionPaperOutcome.Failed(failure)
}
