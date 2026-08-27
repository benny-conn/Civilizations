package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.damage.JournalActorRelationship
import io.bennyc.civilizations.application.damage.PrepareBlockMutation
import io.bennyc.civilizations.application.damage.PreparedBlockMutation
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.BattleId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BattleBlockMutationQueueTest {
    @Test
    fun `queue coalesces coordinates applies backpressure and frees capacity on completion`() {
        val callbacks = linkedMapOf<PrepareBlockMutation, (RuntimeMutationOutcome<PreparedBlockMutation>) -> Unit>()
        var nanos = 0L
        val queue = BattleBlockMutationQueue(
            prepare = { request, completion -> callbacks[request] = completion },
            maxPending = 1,
            nanoTime = { nanos },
        )
        val first = request(x = 1)
        val second = request(x = 2)
        val completions = mutableListOf<BattleBlockJournalCompletion>()

        assertEquals(BattleBlockQueueSubmission.Accepted, queue.submit(first, completions::add))
        assertEquals(BattleBlockQueueSubmission.Duplicate, queue.submit(first, completions::add))
        assertEquals(BattleBlockQueueSubmission.Saturated, queue.submit(second, completions::add))
        assertEquals(1, queue.metrics().pending)

        nanos = 2_000_000L
        callbacks.getValue(first)(
            RuntimeMutationOutcome.Completed(
                ApplicationResult.Applied(prepared(first)),
                CivilizationsRuntimeState.Ready(activeSeason = null),
            ),
        )
        assertIs<BattleBlockJournalCompletion.Prepared>(completions.single())
        assertEquals(BattleBlockQueueSubmission.Accepted, queue.submit(second, completions::add))
        nanos = 3_000_000L
        callbacks.getValue(second)(
            RuntimeMutationOutcome.Completed(
                ApplicationResult.Unchanged(prepared(second)),
                CivilizationsRuntimeState.Ready(activeSeason = null),
            ),
        )
        assertIs<BattleBlockJournalCompletion.Prepared>(completions[1])

        val metrics = queue.metrics()
        assertEquals(0, metrics.pending)
        assertEquals(1, metrics.peakPending)
        assertEquals(2, metrics.accepted)
        assertEquals(1, metrics.duplicates)
        assertEquals(1, metrics.saturated)
        assertEquals(2, metrics.prepared)
        assertEquals(1.5, metrics.averageJournalMillis)
    }

    @Test
    fun `queue translates rejected unavailable and failed journal outcomes`() {
        val callbacks = mutableListOf<(RuntimeMutationOutcome<PreparedBlockMutation>) -> Unit>()
        val queue = BattleBlockMutationQueue(
            prepare = { _, completion -> callbacks += completion },
        )
        val completions = mutableListOf<BattleBlockJournalCompletion>()

        queue.submit(request(x = 1), completions::add)
        callbacks.removeFirst()(
            RuntimeMutationOutcome.Completed(
                ApplicationResult.Rejected(TestFailure),
                CivilizationsRuntimeState.Ready(activeSeason = null),
            ),
        )
        queue.submit(request(x = 2), completions::add)
        callbacks.removeFirst()(
            RuntimeMutationOutcome.NotReady(CivilizationsRuntimeState.Starting),
        )
        queue.submit(request(x = 3), completions::add)
        callbacks.removeFirst()(RuntimeMutationOutcome.Failed(IllegalStateException("storage")))

        assertIs<BattleBlockJournalCompletion.Rejected>(completions[0])
        assertIs<BattleBlockJournalCompletion.Unavailable>(completions[1])
        assertIs<BattleBlockJournalCompletion.Failed>(completions[2])
        val metrics = queue.metrics()
        assertEquals(0, metrics.pending)
        assertEquals(1, metrics.rejected)
        assertEquals(1, metrics.unavailable)
        assertEquals(1, metrics.failed)
    }

    private fun request(x: Int) = PrepareBlockMutation(
        battleId = battleId,
        claimId = claimId,
        position = BlockPosition3D(world, x, 64, 0),
        observedState = SimpleBlockSnapshot("minecraft:stone"),
        actorId = actorId,
        cause = BlockMutationCause.PLAYER_BREAK,
    )

    private fun prepared(request: PrepareBlockMutation) = PreparedBlockMutation(
        journalEntry = BattleBlockChange(
            id = BlockChangeId(UUID(0, 5)),
            seasonId = seasonId,
            battleId = request.battleId,
            claimId = request.claimId,
            position = request.position,
            originalState = request.observedState,
            firstMutationCause = request.cause,
            firstActorId = request.actorId,
            recordedAt = instant,
        ),
        expectedCurrentState = request.observedState,
        actorId = request.actorId,
        cause = request.cause,
        relationship = JournalActorRelationship.OPPONENT,
        preparedAt = instant,
        capturedOriginalState = true,
    )

    private data object TestFailure : ApplicationFailure {
        override val description: String = "rejected"
    }

    private companion object {
        val world = WorldId("minecraft:overworld")
        val seasonId = SeasonId(UUID(0, 1))
        val battleId = BattleId(UUID(0, 2))
        val claimId = ClaimId(UUID(0, 3))
        val actorId = PlayerId(UUID(0, 4))
        val instant = Instant.parse("2026-08-18T12:00:00Z")
    }
}
