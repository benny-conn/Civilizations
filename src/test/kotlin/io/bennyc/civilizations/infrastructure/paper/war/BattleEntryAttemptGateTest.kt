package io.bennyc.civilizations.infrastructure.paper.war

import io.bennyc.civilizations.domain.identity.PlayerId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BattleEntryAttemptGateTest {
    @Test
    fun `gate bounds pending work and cools repeated boundary attempts`() {
        val gate = BattleEntryAttemptGate(maxPending = 1, cooldownNanos = 100)
        val first = player(1)
        val second = player(2)

        assertEquals(BattleEntryGateDecision.Accepted, gate.begin(first, 1_000))
        assertEquals(BattleEntryGateDecision.Pending, gate.begin(first, 1_001))
        assertEquals(BattleEntryGateDecision.Saturated, gate.begin(second, 1_001))

        gate.complete(first)
        assertEquals(BattleEntryGateDecision.CoolingDown, gate.begin(first, 1_050))
        assertEquals(BattleEntryGateDecision.Accepted, gate.begin(first, 1_100))
        gate.complete(first)

        val metrics = gate.metrics()
        assertEquals(0, metrics.pending)
        assertEquals(1, metrics.peakPending)
        assertEquals(2, metrics.accepted)
        assertEquals(1, metrics.pendingRejected)
        assertEquals(1, metrics.cooldownRejected)
        assertEquals(1, metrics.saturatedRejected)
    }

    @Test
    fun `feedback is throttled and quit forgets player state`() {
        val gate = BattleEntryAttemptGate(cooldownNanos = 100)
        val player = player(1)

        assertTrue(gate.shouldSendFeedback(player, 500))
        assertFalse(gate.shouldSendFeedback(player, 599))
        assertTrue(gate.shouldSendFeedback(player, 600))
        assertEquals(BattleEntryGateDecision.Accepted, gate.begin(player, 600))

        gate.forget(player)

        assertEquals(BattleEntryGateDecision.Accepted, gate.begin(player, 601))
    }

    private fun player(value: Long): PlayerId = PlayerId(UUID(0, value))
}
