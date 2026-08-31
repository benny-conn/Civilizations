package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.war.BattleCombatUpdate
import io.bennyc.civilizations.application.war.RecordBattleLifeLosses
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleLifeEventId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BattleLifeLossQueueTest {
    @Test
    fun `same-tick deaths share one simultaneous battle batch`() {
        val harness = Harness()
        val first = harness.capture(event = 1, player = 1, lives = 1)
        val second = harness.capture(event = 2, player = 2, lives = 1)

        assertEquals(BattleLifeLossSubmission.Accepted, harness.queue.submit(first))
        assertEquals(BattleLifeLossSubmission.Accepted, harness.queue.submit(second))
        assertEquals(1, harness.scheduled.size)
        assertTrue(harness.queue.isFinalLifePending(first.playerId, 1))
        assertTrue(harness.queue.isFinalLifePending(second.playerId, 1))

        harness.runNextTick()

        assertEquals(1, harness.requests.size)
        assertEquals(
            setOf(first.playerId, second.playerId),
            harness.requests.single().losses.mapTo(mutableSetOf()) { it.playerId },
        )
        harness.rejectNext()
        assertFalse(harness.queue.isFinalLifePending(first.playerId, 1))
        assertEquals(0, harness.queue.metrics().pending)
    }

    @Test
    fun `one player is consumed at most once per batch and pending lives stay bounded`() {
        val harness = Harness()
        val first = harness.capture(event = 1, player = 1, lives = 2)
        val second = harness.capture(event = 2, player = 1, lives = 2)

        assertEquals(BattleLifeLossSubmission.Accepted, harness.queue.submit(first))
        assertEquals(BattleLifeLossSubmission.Accepted, harness.queue.submit(second))
        assertEquals(
            BattleLifeLossSubmission.Redundant,
            harness.queue.submit(harness.capture(event = 3, player = 1, lives = 2)),
        )
        assertTrue(harness.queue.isFinalLifePending(first.playerId, 2))

        harness.runNextTick()
        assertEquals(listOf(first.eventId), harness.requests.single().losses.map { it.eventId })
        harness.rejectNext()
        assertEquals(1, harness.scheduled.size)

        harness.runNextTick()
        assertEquals(listOf(second.eventId), harness.requests.single().losses.map { it.eventId })
        harness.rejectNext()
        assertEquals(0, harness.queue.metrics().pending)
        assertEquals(1, harness.queue.metrics().redundant)
    }

    @Test
    fun `duplicate event identity does not enqueue extra work`() {
        val harness = Harness()
        val first = harness.capture(event = 1, player = 1, lives = 1)

        assertEquals(BattleLifeLossSubmission.Accepted, harness.queue.submit(first))
        assertEquals(BattleLifeLossSubmission.Duplicate, harness.queue.submit(first))
        assertEquals(1, harness.queue.metrics().duplicates)
    }

    private class Harness {
        val scheduled = ArrayDeque<() -> Unit>()
        val requests = ArrayDeque<RecordBattleLifeLosses>()
        private val completions = ArrayDeque<
            (RuntimeMutationOutcome<BattleCombatUpdate>) -> Unit
        >()
        val queue = BattleLifeLossQueue(
            record = { request, completion ->
                requests += request
                completions += completion
            },
            scheduleNextTick = scheduled::addLast,
        )

        fun capture(event: Long, player: Long, lives: Int) = BattleLifeLossCapture(
            battleId = BattleId(UUID(0, 100)),
            eventId = BattleLifeEventId(UUID(0, event)),
            playerId = PlayerId(UUID(0, player)),
            observedLivesRemaining = lives,
        )

        fun runNextTick() {
            scheduled.removeFirst().invoke()
        }

        fun rejectNext() {
            requests.removeFirst()
            completions.removeFirst().invoke(
                RuntimeMutationOutcome.Completed(
                    ApplicationResult.Rejected(TestFailure),
                    CivilizationsRuntimeState.Ready(activeSeason = null),
                ),
            )
        }
    }

    private data object TestFailure : ApplicationFailure {
        override val description: String = "expected test rejection"
    }
}
