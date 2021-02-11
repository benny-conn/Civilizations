/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.task

import net.tolmikarc.civilizations.manager.CivManager
import org.bukkit.entity.Monster
import org.bukkit.scheduler.BukkitRunnable

class MobRemovalTask : BukkitRunnable() {
    override fun run() {
        CivManager.all.forEach { civ ->
            if (!civ.toggleables.mobs) {
                civ.claims.claims.forEach { claim ->
                    claim.entities.forEach { entity ->
                        if (entity is Monster) {
                            entity.remove()
                        }
                    }
                }
            }
        }
    }

}