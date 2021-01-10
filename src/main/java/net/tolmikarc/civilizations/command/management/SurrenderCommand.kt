/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class SurrenderCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "surrender") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You must have a civilization to Raid another.")
            civPlayer.civilization?.apply {
                val enemyCiv = CivManager.getByName(args[0])
                checkNotNull(enemyCiv, "Please specify a valid Civ to surrender to")
                if (!warring.contains(enemyCiv)) returnTell("You are not warring this Civilization")
                if (hasCooldown(this, CooldownTask.CooldownType.END_WAR)) {
                    bank.removeBalance(Settings.SURRENDER_COST)
                    enemyCiv?.bank?.addBalance(Settings.SURRENDER_COST)
                }
                enemies.remove(enemyCiv)
                enemyCiv?.addPower(Settings.POWER_WAR_WIN)
            }
        }
    }

    init {
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}