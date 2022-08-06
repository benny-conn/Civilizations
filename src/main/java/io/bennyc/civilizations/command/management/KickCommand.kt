/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker.canManageCiv
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.model.CivPlayer
import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class KickCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "kick") {
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
                    !args[0].equals(player.name, ignoreCase = true),
                    io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_SPECIFY_SELF
                )
                executeCommand(this, PlayerManager.getByName(args[0]))
            }
        }
    }

    private fun executeCommand(civilization: Civilization, kickedCache: CivPlayer?) {
        checkNotNull(
            kickedCache,
            Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
        )
        checkBoolean(civilization.citizens.contains(kickedCache), Localization.Warnings.NOT_IN_CIV)
        civilization.removeCitizen(kickedCache!!)
        tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
    }

    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}