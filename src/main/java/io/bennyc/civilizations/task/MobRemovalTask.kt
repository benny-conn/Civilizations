/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.task

import io.bennyc.civilizations.util.ClaimUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Monster
import org.bukkit.scheduler.BukkitRunnable

class MobRemovalTask : BukkitRunnable() {

    private val mobsToRemove = mutableSetOf<Monster>()

    override fun run() {
        for (world in Bukkit.getWorlds()) {
            world.entities.forEach {
                if (it !is Monster) {
                    return
                }
                val location = it.location
                if (!location.chunk.isLoaded) {
                    return
                }
                val civ = ClaimUtil.getCivFromLocation(location) ?: return
                val plot = ClaimUtil.getPlotFromLocation(location, civ)
                if (plot != null) {
                    if (!plot.toggleables.mobs)
                        mobsToRemove.add(it)
                } else {
                    if (!civ.toggleables.mobs)
                        mobsToRemove.add(it)
                }
            }
        }
        mobsToRemove.forEach { it.remove() }
    }

}