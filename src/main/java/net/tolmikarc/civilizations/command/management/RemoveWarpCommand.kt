/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class RemoveWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "removewarp") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                checkBoolean(
                    warps.containsKey(args[0]),
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp")
                )
                removeWarp(args[0])
                tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
            }
        }
    }

    init {
        setDescription("Remove a warp of your Civilization.")
        usage = "<name>"
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}