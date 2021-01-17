/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AToggleCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "toggle") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            when (args[1].toLowerCase()) {
                "fire" -> toggleables.fire =
                    !toggleables.fire.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.fire.toString()
                            )
                        )
                    }
                "explosions" -> toggleables.explosion =
                    !toggleables.explosion.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.explosion.toString()
                            )
                        )
                    }
                "mobs" -> toggleables.mobs =
                    !toggleables.mobs.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.mobs.toString()
                            )
                        )
                    }
                "pvp" -> toggleables.pvp =
                    !toggleables.pvp.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.pvp.toString()
                            )
                        )
                    }
                "public" -> toggleables.public =
                    !toggleables.public.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.public.toString()
                            )
                        )
                    }
                "inviteonly" -> toggleables.inviteOnly =
                    !toggleables.inviteOnly.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.inviteOnly.toString()
                            )
                        )
                    }
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