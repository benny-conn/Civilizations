package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.damage.PrepareBlockMutation
import io.bennyc.civilizations.application.damage.PreparedBlockMutation
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.war.BattleId

/**
 * Bounds the number of cancelled Paper actions waiting for a durable journal result.
 * Callers submit and receive completions on the server thread; synchronization keeps
 * the invariant explicit and makes the class safe for direct-executor tests as well.
 */
class BattleBlockMutationQueue(
    private val prepare: (
        PrepareBlockMutation,
        (RuntimeMutationOutcome<PreparedBlockMutation>) -> Unit,
    ) -> Unit,
    private val maxPending: Int = DEFAULT_MAX_PENDING,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val lock = Any()
    private val pending = linkedMapOf<PendingMutationKey, Long>()
    private var acceptedCount = 0L
    private var duplicateCount = 0L
    private var saturatedCount = 0L
    private var preparedCount = 0L
    private var rejectedCount = 0L
    private var unavailableCount = 0L
    private var failedCount = 0L
    private var totalJournalNanos = 0L
    private var peakPending = 0

    init {
        require(maxPending > 0) { "Battle block mutation queue bound must be positive" }
    }

    fun submit(
        request: PrepareBlockMutation,
        completion: (BattleBlockJournalCompletion) -> Unit,
    ): BattleBlockQueueSubmission {
        val key = PendingMutationKey(request.battleId, request.position)
        val startedAt = nanoTime()
        val submission = synchronized(lock) {
            when {
                key in pending -> {
                    duplicateCount++
                    BattleBlockQueueSubmission.Duplicate
                }
                pending.size >= maxPending -> {
                    saturatedCount++
                    BattleBlockQueueSubmission.Saturated
                }
                else -> {
                    pending[key] = startedAt
                    acceptedCount++
                    peakPending = maxOf(peakPending, pending.size)
                    BattleBlockQueueSubmission.Accepted
                }
            }
        }
        if (submission != BattleBlockQueueSubmission.Accepted) {
            return submission
        }

        try {
            prepare(request) { outcome ->
                val elapsed = (nanoTime() - startedAt).coerceAtLeast(0L)
                val translated = when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> {
                            synchronized(lock) { preparedCount++ }
                            BattleBlockJournalCompletion.Prepared(result.value)
                        }
                        is ApplicationResult.Unchanged -> {
                            synchronized(lock) { preparedCount++ }
                            BattleBlockJournalCompletion.Prepared(result.value)
                        }
                        is ApplicationResult.Rejected -> {
                            synchronized(lock) { rejectedCount++ }
                            BattleBlockJournalCompletion.Rejected(result.failure)
                        }
                    }
                    is RuntimeMutationOutcome.NotReady -> {
                        synchronized(lock) { unavailableCount++ }
                        BattleBlockJournalCompletion.Unavailable
                    }
                    is RuntimeMutationOutcome.Failed -> {
                        synchronized(lock) { failedCount++ }
                        BattleBlockJournalCompletion.Failed(outcome.failure)
                    }
                }
                synchronized(lock) {
                    check(pending.remove(key) != null) {
                        "Battle mutation completion did not match pending key $key"
                    }
                    totalJournalNanos += elapsed
                }
                completion(translated)
            }
        } catch (failure: Throwable) {
            val removed = synchronized(lock) {
                if (pending.remove(key) == null) {
                    false
                } else {
                    failedCount++
                    totalJournalNanos += (nanoTime() - startedAt).coerceAtLeast(0L)
                    true
                }
            }
            if (!removed) {
                throw failure
            }
            completion(BattleBlockJournalCompletion.Failed(failure))
        }
        return submission
    }

    fun metrics(): BattleBlockMutationQueueMetrics = synchronized(lock) {
        BattleBlockMutationQueueMetrics(
            pending = pending.size,
            peakPending = peakPending,
            accepted = acceptedCount,
            duplicates = duplicateCount,
            saturated = saturatedCount,
            prepared = preparedCount,
            rejected = rejectedCount,
            unavailable = unavailableCount,
            failed = failedCount,
            totalJournalNanos = totalJournalNanos,
        )
    }

    private data class PendingMutationKey(
        val battleId: BattleId,
        val position: BlockPosition3D,
    )

    companion object {
        const val DEFAULT_MAX_PENDING = 256
    }
}

enum class BattleBlockQueueSubmission {
    Accepted,
    Duplicate,
    Saturated,
}

sealed interface BattleBlockJournalCompletion {
    data class Prepared(val mutation: PreparedBlockMutation) : BattleBlockJournalCompletion

    data class Rejected(val failure: ApplicationFailure) : BattleBlockJournalCompletion

    data object Unavailable : BattleBlockJournalCompletion

    data class Failed(val failure: Throwable) : BattleBlockJournalCompletion
}

data class BattleBlockMutationQueueMetrics(
    val pending: Int,
    val peakPending: Int,
    val accepted: Long,
    val duplicates: Long,
    val saturated: Long,
    val prepared: Long,
    val rejected: Long,
    val unavailable: Long,
    val failed: Long,
    val totalJournalNanos: Long,
) {
    val averageJournalMillis: Double
        get() = if (prepared + rejected + unavailable + failed == 0L) {
            0.0
        } else {
            totalJournalNanos.toDouble() /
                (prepared + rejected + unavailable + failed).toDouble() /
                NANOS_PER_MILLISECOND
        }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000.0
    }
}
