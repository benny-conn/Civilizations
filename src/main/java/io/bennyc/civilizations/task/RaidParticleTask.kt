/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.task

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.mineacademy.fo.remain.CompParticle

class RaidParticleTask(val player: Player, private val enemyPlayers: List<Player>) : BukkitRunnable() {

    override fun run() {
        enemyPlayers.forEach {
            CompParticle.FLAME.spawn(player, it.eyeLocation.add(0.0, 1.0, 0.0))
        }

    }


}