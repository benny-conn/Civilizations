package net.tolmikarc.civilizations.listener

import net.tolmikarc.civilizations.util.ClaimUtil.getCivFromLocation
import net.tolmikarc.civilizations.util.ClaimUtil.getPlotFromLocation
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockIgniteEvent

class WorldListener : Listener {
    @EventHandler
    fun onIgnite(event: BlockIgniteEvent) {
        val location = event.block.location
        val civilization = getCivFromLocation(location) ?: return
        val plot = getPlotFromLocation(location, civilization)
        if (plot != null) {
            if (!plot.claimToggleables.fire) event.isCancelled = true
            return
        }
        if (!civilization.claimToggleables.fire) event.isCancelled = true
    }
    
}