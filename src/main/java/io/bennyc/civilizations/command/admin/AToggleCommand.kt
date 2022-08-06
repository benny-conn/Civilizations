/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.concurrent.TimeUnit

class AToggleCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "toggle") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            when (args[1].toLowerCase()) {
                "fire" -> {
                    toggleables.fire = !toggleables.fire
                    tellSuccess(
                        io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.fire.toString()
                        )
                    )

                }
                "explosions" -> {
                    toggleables.explosion = !toggleables.explosion
                    tellSuccess(
                        io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.explosion.toString()
                        )
                    )

                }
                "mobs" -> {
                    toggleables.mobs = !toggleables.mobs
                    tellSuccess(
                        io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.mobs.toString()
                        )
                    )

                }
                "pvp" -> {
                    toggleables.pvp = !toggleables.pvp
                    tellSuccess(
                        io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.pvp.toString()
                        )
                    )
                    setCooldown(io.bennyc.civilizations.settings.Settings.PVP_TOGGLE_COOLDOWN, TimeUnit.SECONDS)

                }
                "public" -> {
                    toggleables.public = !toggleables.public
                    tellSuccess(
                        io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TOGGLE.replace(
                            "{value}",
                            toggleables.public.toString()
                        )
                    )

                }
                "inviteonly" -> {
                    toggleables.inviteOnly = !toggleables.inviteOnly
                    tellSuccess(
                        io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TOGGLE.replace(
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