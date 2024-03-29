/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.listener

import io.bennyc.civilizations.util.ClaimUtil
import io.bennyc.civilizations.util.ClaimUtil.getCivFromLocation
import io.bennyc.civilizations.util.ClaimUtil.getPlotFromLocation
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFromToEvent
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
            if (!plot.toggleables.fire) event.isCancelled = true
            return
        }
        if (!civilization.toggleables.fire) event.isCancelled = true
    }

    @EventHandler
    fun onLavaSpread(event: BlockFromToEvent) {
        val location = event.toBlock.location
        val civilization = getCivFromLocation(location) ?: return
        val plot = getPlotFromLocation(location, civilization)
        if (plot != null) {
            if (!plot.toggleables.fire) event.isCancelled = true
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