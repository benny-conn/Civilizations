/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class LeaveCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "leave") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a Civilization to leave.")
            val civilization = civPlayer.civilization
            checkBoolean(
                civilization!!.leader != civPlayer,
                "If you would like to leave your Civilization as it's leader you must either bestow leadership upon someone else with /civ leader <player> or disband your civilization with /civ delete"
            )

            fun run() {
                civilization.removeCitizen(civPlayer)
                civPlayer.civilization = null
                PlayerManager.queueForSaving(civPlayer)
                CivManager.queueForSaving(civilization)
                tell("{3}Left the Civilization " + civPlayer.civilization)
            }

            ConfirmMenu("&4Leave Civilization?", "You cannot undo this decision.", ::run).displayTo(player)
        }
    }

    init {
        setDescription("Leave your current Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}