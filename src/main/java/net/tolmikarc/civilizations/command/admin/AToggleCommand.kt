/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.concurrent.TimeUnit

class AToggleCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "toggle") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            when (args[1].toLowerCase()) {
                "fire" -> {
                    toggleables.fire = !toggleables.fire
                    tellSuccess(
                        Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.fire.toString()
                        )
                    )

                }
                "explosions" -> {
                    toggleables.explosion = !toggleables.explosion
                    tellSuccess(
                        Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.explosion.toString()
                        )
                    )

                }
                "mobs" -> {
                    toggleables.mobs = !toggleables.mobs
                    tellSuccess(
                        Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.mobs.toString()
                        )
                    )

                }
                "pvp" -> {
                    toggleables.pvp = !toggleables.pvp
                    tellSuccess(
                        Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.pvp.toString()
                        )
                    )
                    setCooldown(Settings.PVP_TOGGLE_COOLDOWN, TimeUnit.SECONDS)

                }
                "public" -> {
                    toggleables.public = !toggleables.public
                    tellSuccess(
                        Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.public.toString()
                        )
                    )

                }
                "inviteonly" -> {
                    toggleables.inviteOnly = !toggleables.inviteOnly
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