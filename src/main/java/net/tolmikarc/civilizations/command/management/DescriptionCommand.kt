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

class DescriptionCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "description") {
    override fun onCommand() {
        checkConsole()
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, Localization.Warnings.NO_CIV)
        checkBoolean(PermissionChecker.canManageCiv(civPlayer, civ!!), Localization.Warnings.CANNOT_MANAGE_CIV)
        civ.description = args[0].also { tellSuccess("{1}Set description to: ${args[0]}") }
    }


    init {
        minArguments = 1
        usage = "<description>"
        if (Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}