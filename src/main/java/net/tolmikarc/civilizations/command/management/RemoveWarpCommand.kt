/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class RemoveWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "removewarp") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a civilization.")
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), "You cannot manage this Civilization")
                checkBoolean(warps.containsKey(args[0]), "Please specify a valid warp")
                removeWarp(args[0])
                tellSuccess("{1}Set a Civilization Warp at your location with the name {2}" + args[0])
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