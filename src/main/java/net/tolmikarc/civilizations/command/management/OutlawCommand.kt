/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class OutlawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "outlaw") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You must have a Civilization to manage it.")
            civPlayer.civilization?.apply {
                checkBoolean(canManageCiv(civPlayer, this), "You cannot manage this Civilization")
                val outlaw = findPlayer(args[0], "Specify a valid and online player")
                PlayerManager.fromBukkitPlayer(outlaw).let { civOutlaw ->
                    checkBoolean(!this.citizens.contains(civOutlaw), "You cannot outlaw a player in your town.")
                    if (this.relationships.outlaws.contains(civOutlaw)) {
                        this.relationships.removeOutlaw(civOutlaw)
                        tell("{2}Successfully removed {1}${args[0]}{1} as an outlaw")
                    } else {
                        this.relationships.addOutlaw(civOutlaw)
                        tell("{2}Successfully outlawed player {1}${args[0]}")
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