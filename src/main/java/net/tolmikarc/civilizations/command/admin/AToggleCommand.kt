/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AToggleCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "toggle") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
            when (args[1].toLowerCase()) {
                "fire" -> toggleables.fire =
                    !toggleables.fire.also { tellSuccess("{1}Toggled ${args[0]}:{2} ${!toggleables.fire}") }
                "explosions" -> toggleables.explosion =
                    !toggleables.explosion.also { tellSuccess("{1}Toggled ${args[0]}:{2} ${!toggleables.explosion}") }
                "mobs" -> toggleables.mobs =
                    !toggleables.mobs.also { tellSuccess("{1}Toggled ${args[0]}:{2} ${!toggleables.mobs}") }
                "pvp" -> toggleables.pvp =
                    !toggleables.pvp.also { tellSuccess("{1}Toggled ${args[0]}:{2} ${!toggleables.pvp}") }
                "public" -> toggleables.public =
                    !toggleables.public.also { tellSuccess("{1}Toggled ${args[0]}:{2} ${!toggleables.public}") }
                "inviteonly" -> toggleables.inviteOnly =
                    !toggleables.inviteOnly.also { tellSuccess("{1}Toggled ${args[0]}:{2} ${!toggleables.inviteOnly}") }
                else -> returnInvalidArgs()
            }
        }


    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 2) listOf("fire", "explosions", "pvp", "mobs", "public", "inviteonly")
        else
            super.tabComplete()
    }

    init {
        minArguments = 2
        usage = "<civ> <setting>"
        description = "Toggle settings for a Civilization"
    }
}