/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class OutlawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "outlaw") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                val outlaw = findPlayer(
                    args[0],
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
                )
                PlayerManager.fromBukkitPlayer(outlaw).let { civOutlaw ->
                    checkBoolean(!this.citizens.contains(civOutlaw), Localization.Warnings.OUTLAW_CITIZEN)
                    if (this.relationships.outlaws.contains(civOutlaw)) {
                        this.relationships.removeOutlaw(civOutlaw)
                        tellSuccess(Localization.Notifications.OUTLAW_REMOVE.replace("{player}", args[0]))
                    } else {
                        this.relationships.addOutlaw(civOutlaw)
                        tellSuccess(Localization.Notifications.OUTLAW_ADD.replace("{player}", args[0]))
                    }
                }
            }
        }
    }

    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Outlaw a player from your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}