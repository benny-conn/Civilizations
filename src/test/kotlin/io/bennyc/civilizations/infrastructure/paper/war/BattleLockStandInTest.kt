package io.bennyc.civilizations.infrastructure.paper.war

import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.BattleId
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BattleLockStandInTest {
    @Test
    fun `documented BattleLock marker resolves its original player`() {
        val owner = UUID(0, 42)
        val entity = standIn(UUID(0, 7), owner.toString())

        assertEquals(PlayerId(owner), BattleLockStandIn.ownerId(entity))
        assertEquals(
            BattleLockStandIn.lifeEventId(entity),
            BattleLockStandIn.lifeEventId(entity),
            "A retry for one stand-in must retain its life-event identity",
        )
        assertNotEquals(
            BattleLockStandIn.lifeEventId(entity),
            BattleLockStandIn.lifeEventId(standIn(UUID(0, 8), owner.toString())),
        )
    }

    @Test
    fun `missing or malformed marker is not treated as a combatant proxy`() {
        assertNull(BattleLockStandIn.ownerId(standIn(UUID(0, 7), null)))
        assertNull(BattleLockStandIn.ownerId(standIn(UUID(0, 8), "not-a-uuid")))
    }

    @Test
    fun `ordinary player death identity deduplicates one dispatch but changes by life`() {
        val battleId = BattleId(UUID(0, 100))
        val playerId = PlayerId(UUID(0, 42))
        val first = PaperBattleDeathIdentity.playerDeath(battleId, playerId, 2, 500)

        assertEquals(
            first,
            PaperBattleDeathIdentity.playerDeath(battleId, playerId, 2, 500),
        )
        assertNotEquals(
            first,
            PaperBattleDeathIdentity.playerDeath(battleId, playerId, 1, 500),
        )
        assertNotEquals(
            first,
            PaperBattleDeathIdentity.playerDeath(battleId, playerId, 2, 501),
        )
    }

    private fun standIn(entityId: UUID, storedOwner: String?): Entity {
        val container = Mockito.mock(PersistentDataContainer::class.java)
        val key = NamespacedKey("battlelock", "combat_log_player_id")
        Mockito.`when`(container.get(key, PersistentDataType.STRING)).thenReturn(storedOwner)
        return Mockito.mock(Entity::class.java).also { entity ->
            Mockito.`when`(entity.uniqueId).thenReturn(entityId)
            Mockito.`when`(entity.persistentDataContainer).thenReturn(container)
        }
    }
}
