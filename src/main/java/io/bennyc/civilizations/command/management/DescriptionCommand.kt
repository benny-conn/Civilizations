/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class DescriptionCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "description") {
    override fun onCommand() {
        checkConsole()
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
        checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, civ!!), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
        civ.description =
            Common.join(args.toMutableList(), " ").also { tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND) }
    }


    init {
        minArguments = 1
        usage = "<description>"
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}