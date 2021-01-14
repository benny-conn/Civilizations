/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.listener

import net.tolmikarc.civilizations.util.ClaimUtil
import net.tolmikarc.civilizations.util.ClaimUtil.getCivFromLocation
import net.tolmikarc.civilizations.util.ClaimUtil.getPlotFromLocation
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent

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
        if (!civilization.toggleables.fire) event.isCancelled = true
    }

    @EventHandler
    fun onPiston(event: BlockPistonExtendEvent) {
        val piston = event.block
        if (ClaimUtil.isLocationInACiv(piston.location)) return
        for (block in event.blocks) {
            if (ClaimUtil.isLocationInACiv(block.location) || ClaimUtil.isLocationInACiv(block.getRelative(event.direction).location))
                event.isCancelled = true
        }
    }

    @EventHandler
    fun onPiston(event: BlockPistonRetractEvent) {
        val piston = event.block
        if (ClaimUtil.isLocationInACiv(piston.location)) return
        for (block in event.blocks) {
            if (ClaimUtil.isLocationInACiv(block.location)) {
                event.isCancelled = true
            }
        }
    }

}