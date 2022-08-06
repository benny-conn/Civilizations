/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker.canManageCiv
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class OutlawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "outlaw") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                val outlaw = findPlayer(
                    args[0],
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER)
                )
                io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(outlaw).let { civOutlaw ->
                    checkBoolean(!this.citizens.contains(civOutlaw), io.bennyc.civilizations.settings.Localization.Warnings.OUTLAW_CITIZEN)
                    if (this.relationships.outlaws.contains(civOutlaw)) {
                        this.relationships.removeOutlaw(civOutlaw)
                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.OUTLAW_REMOVE.replace("{player}", args[0]))
                    } else {
                        this.relationships.addOutlaw(civOutlaw)
                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.OUTLAW_ADD.replace("{player}", args[0]))
                    }
                }
            }
        }
    }

    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Outlaw a player from your Civilization")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}