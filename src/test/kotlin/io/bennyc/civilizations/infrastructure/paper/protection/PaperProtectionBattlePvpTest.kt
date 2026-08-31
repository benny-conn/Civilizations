package io.bennyc.civilizations.infrastructure.paper.protection

import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.application.claim.ClaimSpatialIndex
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.application.protection.ProtectionService
import io.bennyc.civilizations.infrastructure.runtime.ActiveBattleCombatantRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.ActiveBattlePvpAuthorization
import io.bennyc.civilizations.infrastructure.runtime.ActiveSeasonRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.damage.DamageSource
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.mockito.Mockito
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaperProtectionBattlePvpTest {
    @Test
    fun `authorized lethal BattleLock hit is translated before its proxy can be removed`() {
        val harness = Harness(authorized = true)

        harness.listener.onEntityDamage(harness.event)

        assertEquals(
            listOf(harness.ownerId to (harness.standIn as Entity)),
            harness.lethalCaptures,
        )
        Mockito.verify(harness.event, Mockito.never()).isCancelled = true
    }

    @Test
    fun `unauthorized BattleLock hit is cancelled and never translated`() {
        val harness = Harness(authorized = false)

        harness.listener.onEntityDamage(harness.event)

        assertTrue(harness.lethalCaptures.isEmpty())
        Mockito.verify(harness.event).isCancelled = true
    }

    @Test
    fun `stand-in outside battle land keeps ordinary wilderness entity behavior`() {
        val harness = Harness(authorized = false, battleLand = false)

        harness.listener.onEntityDamage(harness.event)

        assertEquals(
            listOf(harness.ownerId to (harness.standIn as Entity)),
            harness.lethalCaptures,
        )
        Mockito.verify(harness.event, Mockito.never()).isCancelled = true
    }

    private class Harness(
        private val authorized: Boolean,
        private val battleLand: Boolean = true,
    ) {
        val actorId = PlayerId(UUID(0, 1))
        val ownerId = PlayerId(UUID(0, 2))
        private val battleId = BattleId(UUID(0, 3))
        private val claimId = ClaimId(UUID(0, 4))
        private val position = BlockPosition2D(WorldId("minecraft:overworld"), 4, 8)
        val lethalCaptures = mutableListOf<Pair<PlayerId, Entity>>()
        private val world = Mockito.mock(World::class.java).also { world ->
            Mockito.`when`(world.key).thenReturn(NamespacedKey("minecraft", "overworld"))
        }
        private val container = Mockito.mock(PersistentDataContainer::class.java).also { data ->
            Mockito.`when`(
                data.get(
                    NamespacedKey("battlelock", "combat_log_player_id"),
                    PersistentDataType.STRING,
                ),
            ).thenReturn(ownerId.toString())
        }
        val standIn = Mockito.mock(Villager::class.java).also { entity ->
            Mockito.`when`(entity.persistentDataContainer).thenReturn(container)
            Mockito.`when`(entity.uniqueId).thenReturn(UUID(0, 20))
            Mockito.`when`(entity.health).thenReturn(4.0)
            Mockito.`when`(entity.location).thenReturn(Location(world, 4.0, 64.0, 8.0))
        }
        private val attacker = Mockito.mock(Player::class.java).also { player ->
            Mockito.`when`(player.uniqueId).thenReturn(actorId.value)
            Mockito.`when`(player.hasPermission(Mockito.anyString())).thenReturn(false)
        }
        private val damageSource = Mockito.mock(DamageSource::class.java).also { source ->
            Mockito.`when`(source.causingEntity).thenReturn(attacker)
        }
        val event = Mockito.mock(EntityDamageByEntityEvent::class.java).also { event ->
            Mockito.`when`(event.damageSource).thenReturn(damageSource)
            Mockito.`when`(event.damager).thenReturn(attacker)
            Mockito.`when`(event.entity).thenReturn(standIn)
            Mockito.`when`(event.finalDamage).thenReturn(4.0)
        }
        private val activeBattle = Mockito.mock(Battle::class.java).also { battle ->
            Mockito.`when`(battle.id).thenReturn(battleId)
        }
        private val activeCombatant =
            Mockito.mock(ActiveBattleCombatantRuntimeState::class.java).also { combatant ->
                Mockito.`when`(combatant.battle).thenReturn(activeBattle)
            }
        private val protection = ProtectionService(
            seasonStatus = SeasonStatus.WAR,
            claimIndex = ClaimSpatialIndex(SeasonId(UUID(0, 30)), emptyList()),
            memberships = emptyList(),
        )
        private val activeSeason = Mockito.mock(ActiveSeasonRuntimeState::class.java).also { state ->
            Mockito.`when`(state.activeBattleCombatant(ownerId)).thenReturn(activeCombatant)
            Mockito.`when`(state.activeBattleAt(position)).thenReturn(
                activeBattle.takeIf { battleLand },
            )
            Mockito.`when`(state.protection).thenReturn(protection)
            Mockito.`when`(state.authorizeBattlePvp(actorId, ownerId, position)).thenReturn(
                if (authorized) {
                    ActiveBattlePvpAuthorization(
                        battleId,
                        claimId,
                        actorId,
                        ownerId,
                        position,
                    )
                } else {
                    null
                },
            )
        }
        private val runtime = Mockito.mock(CivilizationsRuntime::class.java).also { runtime ->
            Mockito.`when`(runtime.state).thenReturn(
                CivilizationsRuntimeState.Ready(activeSeason),
            )
        }
        val listener = PaperProtectionListener(
            runtime = runtime,
            server = Mockito.mock(Server::class.java),
            logger = Logger.getLogger("battle-pvp-protection-test"),
            markAuthorizedBattleLockLethalDamage = { owner, entity ->
                lethalCaptures += owner to entity
            },
        )
    }
}
