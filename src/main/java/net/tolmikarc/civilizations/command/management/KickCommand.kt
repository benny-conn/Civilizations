/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class KickCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "kick") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You must have a Civilization to manage it.")
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    "You must be the Leader or an Official of your Civilization to use this command."
                )
                checkBoolean(!args[0].equals(player.name, ignoreCase = true), "You cannot kick yourself")
                PlayerManager.getByName(args[0])?.let { kicked -> executeCommand(this, kicked) }
            }
        }
    }

    private fun executeCommand(civilization: Civ, kickedCache: CPlayer) {
        checkNotNull(kickedCache, "Specify a valid player")
        checkBoolean(civilization.citizens.contains(kickedCache), "This player is not in your town.")
        civilization.removeCitizen(kickedCache)
        tellSuccess("{2}Successfully kicked player {1}${args[0]}")
    }

    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}