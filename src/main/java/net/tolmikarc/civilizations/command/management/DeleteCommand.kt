/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.event.DeleteCivEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class DeleteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "delete") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.let { civ ->
                checkBoolean(civ.leader == civPlayer, Localization.Warnings.LEADER)
                val info = "Are you sure you would like to delete your Civilization?"
                val title = "&4Delete Civilization?"


                fun run() {
                    tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                    for (citizen in civ.citizens) {
                        citizen.civilization = null
                        PlayerManager.queueForSaving(citizen)
                    }
                    CivManager.removeCiv(civ)
                    Common.callEvent(DeleteCivEvent(civ, player))
                }
                ConfirmMenu(title, info, ::run).displayTo(player)
            }
        }
    }

    init {
        setDescription("Delete your Civilization.")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}