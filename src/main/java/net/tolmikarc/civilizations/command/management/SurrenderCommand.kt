/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
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
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), "You cannot manage this Civilization")
                val enemyCiv = CivManager.getByName(args[0])
                checkNotNull(enemyCiv, "Please specify a valid Civ to surrender to")
                if (!relationships.warring.contains(enemyCiv)) returnTell("You are not warring this Civilization")
                fun run() {
                    if (hasCooldown(this, CooldownTask.CooldownType.END_WAR)) {
                        checkBoolean(
                            bank.balance - Settings.SURRENDER_COST < 0,
                            "You do not have enough money to surrender in your Civ. Required amount: ${Settings.SURRENDER_COST}"
                        )
                        bank.removeBalance(Settings.SURRENDER_COST)
                        enemyCiv?.bank?.addBalance(Settings.SURRENDER_COST)
                    }
                    relationships.enemies.remove(enemyCiv)
                    enemyCiv?.addPower(Settings.POWER_WAR_WIN)
                    tellSuccess("&4Surrendered to ${enemyCiv?.name}")
                }
                ConfirmMenu("&4Surrender?", "Give up the war with ${enemyCiv!!.name}", ::run).displayTo(player)
            }
        }
    }

    init {
        minArguments = 1
        usage = "<enemy>"
        setDescription("Surrender a war with another Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}