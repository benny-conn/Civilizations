/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
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
                checkBoolean(!args[0].equals(player.name, ignoreCase = true), Localization.Warnings.CANNOT_SPECIFY_SELF)
                executeCommand(this, PlayerManager.getByName(args[0]))
            }
        }
    }

    private fun executeCommand(civilization: Civ, kickedCache: CPlayer?) {
        checkNotNull(
            kickedCache,
            Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
        )
        checkBoolean(civilization.citizens.contains(kickedCache), "This player is not in your town.")
        civilization.removeCitizen(kickedCache!!)
        tellSuccess("{2}Successfully kicked player {1}${args[0]}")
    }

    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}