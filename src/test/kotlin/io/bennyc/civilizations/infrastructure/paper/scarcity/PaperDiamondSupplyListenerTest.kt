package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.DiamondActivation
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.papermc.paper.event.player.PlayerPurchaseEvent
import io.papermc.paper.event.player.PlayerTradeEvent
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.block.BlockDispenseLootEvent
import org.bukkit.event.entity.VillagerAcquireTradeEvent
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.loot.LootContext
import org.bukkit.loot.LootTable
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class PaperDiamondSupplyListenerTest {
    private val activation = DiamondActivation(SeasonId(UUID(0,1)), true, "a".repeat(64), "console", "test", Instant.EPOCH)
    private var state: CivilizationsRuntimeState = CivilizationsRuntimeState.Ready(null, diamondActivation = activation)
    private val listener = PaperDiamondSupplyListener { state }
    private fun item(material: Material) = mock(ItemStack::class.java).also { `when`(it.type).thenReturn(material) }

    @Test fun `container and vault loot remove selected outputs but leave other rewards`() {
        val diamond = item(Material.DIAMOND); val emerald = item(Material.EMERALD)
        val loot = LootGenerateEvent(mock(World::class.java), null, null, mock(LootTable::class.java), mock(LootContext::class.java), mutableListOf(diamond, emerald), false)
        listener.onLoot(loot)
        assertEquals(listOf(emerald), loot.loot)
        val vault = BlockDispenseLootEvent(null, mock(Block::class.java), mutableListOf(diamond, emerald), mock(LootTable::class.java))
        listener.onVault(vault)
        assertEquals(listOf(emerald), vault.dispensedLoot)
        assertFalse(vault.isCancelled)
    }

    @Test fun `old offers and standalone merchants cannot bypass acquisition restrictions`() {
        val recipe = mock(MerchantRecipe::class.java)
        val output = item(Material.DIAMOND_CHESTPLATE)
        `when`(recipe.result).thenReturn(output)
        val villager = mock(Villager::class.java)
        val acquire = VillagerAcquireTradeEvent(villager, recipe)
        listener.onAcquireTrade(acquire); assertTrue(acquire.isCancelled)
        val old = PlayerTradeEvent(mock(Player::class.java), villager, recipe, true, true)
        listener.onPurchase(old); assertTrue(old.isCancelled)
        assertSame(PlayerPurchaseEvent.getHandlerList(), old.handlers)
        state = CivilizationsRuntimeState.Ready(null, diamondActivation = activation.copy(includeEquipment = false))
        val permitted = PlayerTradeEvent(mock(Player::class.java), villager, recipe, true, true)
        listener.onPurchase(permitted); assertFalse(permitted.isCancelled)
    }

    @Test fun `new creature equipment cannot introduce renewable diamonds`() {
        val mob = mock(org.bukkit.entity.Zombie::class.java)
        val equipment = mock(org.bukkit.inventory.EntityEquipment::class.java)
        val diamond = item(Material.DIAMOND_HELMET)
        val air = item(Material.AIR)
        `when`(mob.equipment).thenReturn(equipment)
        `when`(equipment.armorContents).thenReturn(arrayOf(diamond))
        `when`(equipment.itemInMainHand).thenReturn(air)
        `when`(equipment.itemInOffHand).thenReturn(air)
        val spawn = org.bukkit.event.entity.CreatureSpawnEvent(mob, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL)
        listener.onCreatureSpawn(spawn)
        assertTrue(spawn.isCancelled)
    }

    @Test fun `disabled policy permits loot while startup and failure close the supply window`() {
        val recipe = mock(MerchantRecipe::class.java)
        val output = item(Material.DIAMOND)
        `when`(recipe.result).thenReturn(output)
        state = CivilizationsRuntimeState.Ready(null)
        fun trade() = VillagerAcquireTradeEvent(mock(Villager::class.java), recipe).also(listener::onAcquireTrade)
        assertFalse(trade().isCancelled)
        state = CivilizationsRuntimeState.Starting
        assertTrue(trade().isCancelled)
        state = CivilizationsRuntimeState.Failed(IllegalStateException("storage"))
        assertTrue(trade().isCancelled)
    }
}
