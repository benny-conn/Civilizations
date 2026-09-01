package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.protection.PrepareExposureMutation
import io.bennyc.civilizations.application.protection.PreparedExposureMutation
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.protection.ExposureDamageEvent
import io.bennyc.civilizations.domain.protection.ExposureDamageEventId
import io.bennyc.civilizations.domain.protection.ExposureDamageSite
import io.bennyc.civilizations.domain.protection.ExposureDamageSiteId
import io.bennyc.civilizations.domain.protection.LandExposureId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExposureBlockMutationQueueTest {
    @Test
    fun `queue coalesces positions applies backpressure and releases capacity`() {
        val callbacks = linkedMapOf<PrepareExposureMutation, (RuntimeMutationOutcome<PreparedExposureMutation>) -> Unit>()
        val queue = ExposureBlockMutationQueue(
            prepare = { request, completion -> callbacks[request] = completion },
            maxPending = 1,
        )
        val completions = mutableListOf<ExposureJournalCompletion>()
        val first = request(1)
        val second = request(2)

        assertEquals(ExposureQueueSubmission.Accepted, queue.submit(first, completions::add))
        assertEquals(ExposureQueueSubmission.Duplicate, queue.submit(first, completions::add))
        assertEquals(ExposureQueueSubmission.Saturated, queue.submit(second, completions::add))
        callbacks.getValue(first)(
            RuntimeMutationOutcome.Completed(
                ApplicationResult.Applied(prepared(first)),
                CivilizationsRuntimeState.Ready(null),
            ),
        )
        assertIs<ExposureJournalCompletion.Prepared>(completions.single())
        assertEquals(ExposureQueueSubmission.Accepted, queue.submit(second, completions::add))
        callbacks.getValue(second)(
            RuntimeMutationOutcome.Completed(
                ApplicationResult.Rejected(TestFailure),
                CivilizationsRuntimeState.Ready(null),
            ),
        )
        assertIs<ExposureJournalCompletion.Rejected>(completions[1])
        assertEquals(0, queue.metrics().pending)
        assertEquals(2, queue.metrics().accepted)
        assertEquals(1, queue.metrics().duplicates)
        assertEquals(1, queue.metrics().saturated)
        assertEquals(1, queue.metrics().prepared)
        assertEquals(1, queue.metrics().rejected)
    }

    private fun request(x: Int) = PrepareExposureMutation(
        exposureId,
        ownerId,
        claimId,
        BlockPosition3D(world, x, 64, 0),
        SimpleBlockSnapshot("minecraft:stone"),
        SimpleBlockSnapshot("minecraft:air"),
        playerId,
        actorId,
        BlockMutationCause.PLAYER_BREAK,
    )

    private fun prepared(request: PrepareExposureMutation): PreparedExposureMutation {
        val site = ExposureDamageSite(
            siteId,
            seasonId,
            ownerId,
            exposureId,
            claimId,
            request.position,
            request.observedState,
            instant,
            null,
        )
        return PreparedExposureMutation(
            site,
            ExposureDamageEvent(
                eventId,
                siteId,
                1,
                playerId,
                actorId,
                request.cause,
                request.observedState,
                request.expectedState,
                instant,
            ),
            true,
        )
    }

    private data object TestFailure : ApplicationFailure {
        override val description = "rejected"
    }

    private companion object {
        val seasonId = SeasonId(UUID(0, 1))
        val exposureId = LandExposureId(UUID(0, 2))
        val ownerId = CivilizationId(UUID(0, 3))
        val actorId = CivilizationId(UUID(0, 4))
        val playerId = PlayerId(UUID(0, 5))
        val claimId = ClaimId(UUID(0, 6))
        val siteId = ExposureDamageSiteId(UUID(0, 7))
        val eventId = ExposureDamageEventId(UUID(0, 8))
        val world = WorldId("minecraft:overworld")
        val instant = Instant.parse("2026-08-31T12:00:00Z")
    }
}
