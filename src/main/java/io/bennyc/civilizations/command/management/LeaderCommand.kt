/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker.canManageCiv
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.menu.ConfirmMenu
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class LeaderCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "leader") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV
                )
                checkBoolean(!args[0].equals(player.name, ignoreCase = true), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_SPECIFY_SELF)
                val newLeader = io.bennyc.civilizations.manager.PlayerManager.getByName(args[0])
                fun run() {
                    checkNotNull(
                        newLeader,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER)
                    )
                    checkBoolean(citizens.contains(newLeader), io.bennyc.civilizations.settings.Localization.Warnings.NOT_IN_CIV)
                    leader = newLeader
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
                }
                io.bennyc.civilizations.menu.ConfirmMenu("&4&lSet New Leader?", "WARNING: Irreversible", ::run)
            }
        }
    }


    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}