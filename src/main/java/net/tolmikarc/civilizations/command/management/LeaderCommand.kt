/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class LeaderCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "leader") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    Localization.Warnings.CANNOT_MANAGE_CIV
                )
                checkBoolean(!args[0].equals(player.name, ignoreCase = true), Localization.Warnings.CANNOT_SPECIFY_SELF)
                val newLeader = PlayerManager.getByName(args[0])
                fun run() {
                    checkNotNull(
                        newLeader,
                        Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
                    )
                    checkBoolean(citizens.contains(newLeader), Localization.Warnings.NOT_IN_CIV)
                    leader = newLeader
                    tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                }
                ConfirmMenu("&4&lSet New Leader?", "WARNING: Irreversible", ::run)
            }
        }
    }


    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}