/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.PermissionUtil.canManageCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class OutlawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "outlaw") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You must have a Civilization to manage it.")
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    "You must be the Leader or an Official of your Civilization to use this command."
                )
                val outlaw = findPlayer(args[0], "Specify a valid and online player")
                CivPlayer.fromBukkitPlayer(outlaw)?.let { civOutlaw ->
                    checkBoolean(!this.citizens.contains(civOutlaw), "You cannot outlaw a player in your town.")
                    if (this.outlaws.contains(civOutlaw)) {
                        this.removeOutlaw(civOutlaw)
                        tell("${Settings.SECONDARY_COLOR}Successfully removed ${Settings.PRIMARY_COLOR}${args[0]}${Settings.PRIMARY_COLOR} as an outlaw")
                    } else {
                        this.addOutlaw(civOutlaw)
                        tell("${Settings.SECONDARY_COLOR}Successfully outlawed player ${Settings.PRIMARY_COLOR}${args[0]}")
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