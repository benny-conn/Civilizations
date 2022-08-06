/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.concurrent.TimeUnit

class ToggleCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "toggle") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            civPlayer.civilization?.apply {
                checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                when (args[0].toLowerCase()) {
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

    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 1) listOf("fire", "explosions", "pvp", "mobs", "public", "inviteonly")
        else
            super.tabComplete()
    }

    init {
        minArguments = 1
        description = "Toggle settings for your Civilization"
        usage = "<fire | explosions | pvp | mobs | public | inviteonly >"
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}