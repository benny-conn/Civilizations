/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.task

import net.tolmikarc.civilizations.util.ClaimUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Monster
import org.bukkit.scheduler.BukkitRunnable

class MobRemovalTask : BukkitRunnable() {

    private val mobsToRemove = mutableSetOf<Monster>()

    override fun run() {
        for (world in Bukkit.getWorlds()) {
            world.entities.forEach {
                if (it is Monster) {
                    val location = it.location
                    if (location.chunk.isLoaded) {
                        val civ = ClaimUtil.getCivFromLocation(location)
                        if (civ != null) {
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
                }
            }
        }
        mobsToRemove.forEach { it.remove() }
    }

}