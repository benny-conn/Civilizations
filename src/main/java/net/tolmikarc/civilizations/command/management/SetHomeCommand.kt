/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class SetHomeCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "sethome") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    Localization.Warnings.CANNOT_MANAGE_CIV
                )
                checkBoolean(
                    isLocationInCiv(player.location, this),
                    Localization.Warnings.Claim.NO_CLAIM
                )
                home = player.location
                tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
            }
        }
    }

    init {
        setDescription("Set your Civilizations home location.")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}