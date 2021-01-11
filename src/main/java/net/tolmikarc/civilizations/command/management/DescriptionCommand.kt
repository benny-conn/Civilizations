/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class DescriptionCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "description") {
    override fun onCommand() {
        checkConsole()
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, "You must have a Civ to use this command.")
        checkBoolean(PermissionChecker.canManageCiv(civPlayer, civ!!), "You do not have permission to do this command")
        civ.description = args[0].also { tellSuccess("${Settings.PRIMARY_COLOR}Set description to: ${args[0]}") }
    }


    init {
        minArguments = 1
        usage = "<description>"
        if (Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}