/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.event.DeleteCivEvent
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.menu.ConfirmMenu
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class DeleteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "delete") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.let { civ ->
                checkBoolean(civ.leader == civPlayer, io.bennyc.civilizations.settings.Localization.Warnings.LEADER)
                val info = "Are you sure you would like to delete your Civilization?"
                val title = "&4Delete Civilization?"


                fun run() {
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
                    for (citizen in civ.citizens) {
                        citizen.civilization = null
                        io.bennyc.civilizations.manager.PlayerManager.saveAsync(citizen)
                    }
                    io.bennyc.civilizations.manager.CivManager.removeCiv(civ)
                    Common.callEvent(io.bennyc.civilizations.event.DeleteCivEvent(civ, player))
                }
                io.bennyc.civilizations.menu.ConfirmMenu(title, info, ::run).displayTo(player)
            }
        }
    }

    init {
        setDescription("Delete your Civilization.")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}