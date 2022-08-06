/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class RemoveWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "removewarp") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                checkBoolean(
                    warps.containsKey(args[0]),
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp")
                )
                removeWarp(args[0])
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
            }
        }
    }

    init {
        setDescription("Remove a warp of your Civilization.")
        usage = "<name>"
        minArguments = 1
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}