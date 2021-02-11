/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.task

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.mineacademy.fo.remain.CompParticle

class RaidParticleTask(val player: Player, val enemyPlayers: List<Player>) : BukkitRunnable() {

    override fun run() {
        enemyPlayers.forEach {
            CompParticle.FLAME.spawnFor(player, it.eyeLocation.add(0.0, 1.0, 0.0))
        }

    }


}