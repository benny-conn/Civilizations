package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.war.BattleCombatUpdate
import io.bennyc.civilizations.application.war.BattleLifeLoss
import io.bennyc.civilizations.application.war.RecordBattleLifeLosses
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleLifeEventId

/**
 * Coalesces Paper deaths into one per-battle batch on the next server tick. The queue
 * keeps at most the currently observed remaining lives for any combatant, so repeated
 * callbacks cannot grow without bound while a final elimination is being persisted.
 * Runtime completions and scheduled flushes are expected on the server thread.
 */
class BattleLifeLossQueue(
    private val record: (
        RecordBattleLifeLosses,
        (RuntimeMutationOutcome<BattleCombatUpdate>) -> Unit,
    ) -> Unit,
    private val scheduleNextTick: (() -> Unit) -> Unit,
    private val onCompletion: (BattleLifeLossCompletion) -> Unit = {},
) : AutoCloseable {
    private val queuedByBattle = linkedMapOf<
        BattleId,
        LinkedHashMap<PlayerId, ArrayDeque<BattleLifeLoss>>,
    >()
    private val inFlightBattles = mutableSetOf<BattleId>()
    private val activeEventIds = mutableSetOf<BattleLifeEventId>()
    private val pendingByPlayer = mutableMapOf<PlayerId, Int>()
    private var flushScheduled = false
    private var closed = false
    private val mutableMetrics = MutableBattleLifeLossQueueMetrics()

    fun submit(capture: BattleLifeLossCapture): BattleLifeLossSubmission {
        if (closed) return BattleLifeLossSubmission.Closed
        if (capture.eventId in activeEventIds) {
            mutableMetrics.duplicates++
            return BattleLifeLossSubmission.Duplicate
        }
        val alreadyPending = pendingByPlayer[capture.playerId] ?: 0
        if (alreadyPending >= capture.observedLivesRemaining) {
            mutableMetrics.redundant++
            return BattleLifeLossSubmission.Redundant
        }
        queuedByBattle
            .getOrPut(capture.battleId, ::linkedMapOf)
            .getOrPut(capture.playerId, ::ArrayDeque)
            .addLast(BattleLifeLoss(capture.eventId, capture.playerId))
        activeEventIds += capture.eventId
        pendingByPlayer[capture.playerId] = alreadyPending + 1
        mutableMetrics.accepted++
        mutableMetrics.peakPending = maxOf(mutableMetrics.peakPending, activeEventIds.size)
        scheduleFlush()
        return BattleLifeLossSubmission.Accepted
    }

    /** Conservative hot-path overlay while a final-life database update is in flight. */
    fun isFinalLifePending(playerId: PlayerId, observedLivesRemaining: Int): Boolean =
        (pendingByPlayer[playerId] ?: 0) >= observedLivesRemaining

    fun metrics(): BattleLifeLossQueueMetrics = BattleLifeLossQueueMetrics(
        pending = activeEventIds.size,
        peakPending = mutableMetrics.peakPending,
        accepted = mutableMetrics.accepted,
        duplicates = mutableMetrics.duplicates,
        redundant = mutableMetrics.redundant,
        applied = mutableMetrics.applied,
        unchanged = mutableMetrics.unchanged,
        rejected = mutableMetrics.rejected,
        unavailable = mutableMetrics.unavailable,
        failed = mutableMetrics.failed,
    )

    override fun close() {
        closed = true
        queuedByBattle.clear()
        inFlightBattles.clear()
        activeEventIds.clear()
        pendingByPlayer.clear()
        flushScheduled = false
    }

    private fun scheduleFlush() {
        if (flushScheduled || closed) return
        flushScheduled = true
        scheduleNextTick(::flush)
    }

    private fun flush() {
        flushScheduled = false
        if (closed) return
        val ready = queuedByBattle.keys.filterNot(inFlightBattles::contains)
        for (battleId in ready) {
            val perPlayer = queuedByBattle[battleId] ?: continue
            val batch = perPlayer.values.mapNotNull { losses ->
                losses.removeFirstOrNull()
            }
            perPlayer.entries.removeIf { (_, losses) -> losses.isEmpty() }
            if (perPlayer.isEmpty()) queuedByBattle.remove(battleId)
            if (batch.isEmpty()) continue

            inFlightBattles += battleId
            val request = RecordBattleLifeLosses(battleId, batch)
            try {
                record(request) { outcome -> complete(request, outcome) }
            } catch (failure: Throwable) {
                complete(request, RuntimeMutationOutcome.Failed(failure))
            }
        }
    }

    private fun complete(
        request: RecordBattleLifeLosses,
        outcome: RuntimeMutationOutcome<BattleCombatUpdate>,
    ) {
        check(inFlightBattles.remove(request.battleId)) {
            "Battle ${request.battleId} life-loss completion was not in flight"
        }
        request.losses.forEach { loss ->
            check(activeEventIds.remove(loss.eventId)) {
                "Battle life-loss completion did not match ${loss.eventId}"
            }
            val remaining = checkNotNull(pendingByPlayer[loss.playerId]) - 1
            if (remaining == 0) pendingByPlayer.remove(loss.playerId)
            else pendingByPlayer[loss.playerId] = remaining
        }

        val completion = when (outcome) {
            is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                is ApplicationResult.Applied -> {
                    mutableMetrics.applied++
                    BattleLifeLossCompletion.Applied(result.value, outcome.state)
                }
                is ApplicationResult.Unchanged -> {
                    mutableMetrics.unchanged++
                    BattleLifeLossCompletion.Unchanged(result.value, outcome.state)
                }
                is ApplicationResult.Rejected -> {
                    mutableMetrics.rejected++
                    BattleLifeLossCompletion.Rejected(result.failure)
                }
            }
            is RuntimeMutationOutcome.NotReady -> {
                mutableMetrics.unavailable++
                BattleLifeLossCompletion.Unavailable
            }
            is RuntimeMutationOutcome.Failed -> {
                mutableMetrics.failed++
                BattleLifeLossCompletion.Failed(outcome.failure)
            }
        }
        onCompletion(completion)
        if (queuedByBattle.isNotEmpty()) scheduleFlush()
    }
}

data class BattleLifeLossCapture(
    val battleId: BattleId,
    val eventId: BattleLifeEventId,
    val playerId: PlayerId,
    val observedLivesRemaining: Int,
) {
    init {
        require(observedLivesRemaining > 0) {
            "A life loss can be captured only for a living combatant"
        }
    }
}

enum class BattleLifeLossSubmission {
    Accepted,
    Duplicate,
    Redundant,
    Closed,
}

sealed interface BattleLifeLossCompletion {
    data class Applied(
        val update: BattleCombatUpdate,
        val state: CivilizationsRuntimeState.Ready,
    ) : BattleLifeLossCompletion

    data class Unchanged(
        val update: BattleCombatUpdate,
        val state: CivilizationsRuntimeState.Ready,
    ) : BattleLifeLossCompletion

    data class Rejected(val failure: ApplicationFailure) : BattleLifeLossCompletion

    data object Unavailable : BattleLifeLossCompletion

    data class Failed(val failure: Throwable) : BattleLifeLossCompletion
}

data class BattleLifeLossQueueMetrics(
    val pending: Int,
    val peakPending: Int,
    val accepted: Long,
    val duplicates: Long,
    val redundant: Long,
    val applied: Long,
    val unchanged: Long,
    val rejected: Long,
    val unavailable: Long,
    val failed: Long,
)

private data class MutableBattleLifeLossQueueMetrics(
    var peakPending: Int = 0,
    var accepted: Long = 0,
    var duplicates: Long = 0,
    var redundant: Long = 0,
    var applied: Long = 0,
    var unchanged: Long = 0,
    var rejected: Long = 0,
    var unavailable: Long = 0,
    var failed: Long = 0,
)
