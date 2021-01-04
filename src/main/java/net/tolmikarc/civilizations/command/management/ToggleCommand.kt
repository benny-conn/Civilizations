/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ToggleCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "toggle") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            civPlayer.civilization?.apply {
                when (args[0].toLowerCase()) {
                    "fire" -> claimToggleables.fire =
                        !claimToggleables.fire.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!claimToggleables.fire}") }
                    "explosions" -> claimToggleables.explosion =
                        !claimToggleables.explosion.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!claimToggleables.explosion}") }
                    "mobs" -> claimToggleables.mobs =
                        !claimToggleables.mobs.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!claimToggleables.mobs}") }
                    "pvp" -> claimToggleables.pvp =
                        !claimToggleables.pvp.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!claimToggleables.pvp}") }
                    "public" -> claimToggleables.public =
                        !claimToggleables.public.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!claimToggleables.public}") }
                    "inviteonly" -> claimToggleables.inviteOnly =
                        !claimToggleables.inviteOnly.also { tellSuccess("${Settings.PRIMARY_COLOR}Toggled ${args[0]}:${Settings.SECONDARY_COLOR} ${!claimToggleables.inviteOnly}") }
                    else -> returnInvalidArgs()
                }
            }
        }

    }

    override fun tabComplete(): List<String> {
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