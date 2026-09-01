package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.protection.PrepareExposureMutation
import io.bennyc.civilizations.application.protection.PreparedExposureMutation
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.protection.LandExposureId

/** Bounded/coalesced queue for journal-before-mutation exposed-land actions. */
class ExposureBlockMutationQueue(
    private val prepare: (
        PrepareExposureMutation,
        (RuntimeMutationOutcome<PreparedExposureMutation>) -> Unit,
    ) -> Unit,
    private val maxPending: Int = DEFAULT_MAX_PENDING,
) {
    private val lock = Any()
    private val pending = linkedSetOf<Key>()
    private var accepted = 0L
    private var duplicates = 0L
    private var saturated = 0L
    private var prepared = 0L
    private var rejected = 0L
    private var failed = 0L

    fun submit(
        request: PrepareExposureMutation,
        completion: (ExposureJournalCompletion) -> Unit,
    ): ExposureQueueSubmission {
        val key = Key(request.exposureId, request.position)
        val submission = synchronized(lock) {
            when {
                key in pending -> ExposureQueueSubmission.Duplicate.also { duplicates++ }
                pending.size >= maxPending -> ExposureQueueSubmission.Saturated.also { saturated++ }
                else -> ExposureQueueSubmission.Accepted.also {
                    pending += key
                    accepted++
                }
            }
        }
        if (submission != ExposureQueueSubmission.Accepted) return submission
        try {
            prepare(request) { outcome ->
                val translated = when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> ExposureJournalCompletion.Prepared(result.value)
                            .also { synchronized(lock) { prepared++ } }
                        is ApplicationResult.Unchanged -> ExposureJournalCompletion.Prepared(result.value)
                            .also { synchronized(lock) { prepared++ } }
                        is ApplicationResult.Rejected -> ExposureJournalCompletion.Rejected(result.failure)
                            .also { synchronized(lock) { rejected++ } }
                    }
                    is RuntimeMutationOutcome.NotReady -> ExposureJournalCompletion.Unavailable
                    is RuntimeMutationOutcome.Failed -> ExposureJournalCompletion.Failed(outcome.failure)
                        .also { synchronized(lock) { failed++ } }
                }
                synchronized(lock) { check(pending.remove(key)) }
                completion(translated)
            }
        } catch (failure: Throwable) {
            synchronized(lock) {
                pending.remove(key)
                failed++
            }
            completion(ExposureJournalCompletion.Failed(failure))
        }
        return submission
    }

    fun metrics() = synchronized(lock) {
        ExposureQueueMetrics(pending.size, accepted, duplicates, saturated, prepared, rejected, failed)
    }

    private data class Key(val exposureId: LandExposureId, val position: BlockPosition3D)

    companion object {
        const val DEFAULT_MAX_PENDING = 128
    }
}

enum class ExposureQueueSubmission { Accepted, Duplicate, Saturated }

sealed interface ExposureJournalCompletion {
    data class Prepared(val mutation: PreparedExposureMutation) : ExposureJournalCompletion
    data class Rejected(val failure: ApplicationFailure) : ExposureJournalCompletion
    data object Unavailable : ExposureJournalCompletion
    data class Failed(val failure: Throwable) : ExposureJournalCompletion
}

data class ExposureQueueMetrics(
    val pending: Int,
    val accepted: Long,
    val duplicates: Long,
    val saturated: Long,
    val prepared: Long,
    val rejected: Long,
    val failed: Long,
)
