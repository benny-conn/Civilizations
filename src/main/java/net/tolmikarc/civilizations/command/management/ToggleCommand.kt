/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ToggleCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "toggle") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), "You cannot manage this Civilization")
                when (args[0].toLowerCase()) {
                    "fire" -> toggleables.fire =
                        !toggleables.fire.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!toggleables.fire}") }
                    "explosions" -> toggleables.explosion =
                        !toggleables.explosion.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!toggleables.explosion}") }
                    "mobs" -> toggleables.mobs =
                        !toggleables.mobs.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!toggleables.mobs}") }
                    "pvp" -> toggleables.pvp =
                        !toggleables.pvp.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!toggleables.pvp}") }
                    "public" -> toggleables.public =
                        !toggleables.public.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!toggleables.public}") }
                    "inviteonly" -> toggleables.inviteOnly =
                        !toggleables.inviteOnly.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!toggleables.inviteOnly}") }
                    else -> returnInvalidArgs()
                }
            }
        }

    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 1) listOf("fire", "explosions", "pvp", "mobs", "public", "inviteonly")
        else
            super.tabComplete()
    }

    init {
        minArguments = 1
        description = "Toggle settings for your Civilization"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}