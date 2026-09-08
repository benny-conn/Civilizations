package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.DiamondSupplyPolicy
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.papermc.paper.event.player.PlayerPurchaseEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDispenseLootEvent
import org.bukkit.event.entity.VillagerAcquireTradeEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.inventory.ItemStack

/** Server-wide alternate supply policy. No inventory sweeps, chunk loading or event-thread SQL. */
class PaperDiamondSupplyListener(private val state: () -> CivilizationsRuntimeState) : Listener {
    private fun denied(item: ItemStack): Boolean {
        val ready = state() as? CivilizationsRuntimeState.Ready
            ?: return DiamondSupplyPolicy.excludes(item.type.key.asString(), true)
        val activation = ready.diamondActivation ?: return false
        return DiamondSupplyPolicy.excludes(item.type.key.asString(), activation.includeEquipment)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLoot(event: LootGenerateEvent) { event.loot.removeIf(::denied) }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVault(event: BlockDispenseLootEvent) {
        event.setDispensedLoot(event.dispensedLoot.filterNot(::denied))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onAcquireTrade(event: VillagerAcquireTradeEvent) {
        if (denied(event.recipe.result)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val equipment = event.entity.equipment ?: return
        if ((equipment.armorContents.filterNotNull() + listOf(equipment.itemInMainHand, equipment.itemInOffHand)).any(::denied)) {
            event.isCancelled = true
        }
    }

    // PlayerTradeEvent shares this handler list; also covers standalone plugin merchants.
    // Checking each purchase covers existing offers, restocks, and imported/cured villagers.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPurchase(event: PlayerPurchaseEvent) {
        if (denied(event.trade.result)) event.isCancelled = true
    }
}
