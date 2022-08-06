/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.papermc.lib.PaperLib
import io.bennyc.civilizations.PermissionChecker.isAdmin
import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import io.bennyc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import io.bennyc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class HomeCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "home|tp") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (args.isNotEmpty()) {
                io.bennyc.civilizations.manager.CivManager.getByName(args[0])
                    ?.let { civ ->
                        if (isAdmin(civPlayer) && civ.home != null) {
                            PaperLib.teleportAsync(player, civ.home!!).thenAccept {
                                if (it)
                                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TELEPORT)
                                else
                                    tellError(io.bennyc.civilizations.settings.Localization.Warnings.FAILED_TELEPORT)
                            }
                            return
                        }
                        if (civ.toggleables.public) {
                            civ.home?.let { home ->
                                checkBoolean(
                                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                                    io.bennyc.civilizations.settings.Localization.Warnings.COOLDOWN_WAIT.replace(
                                        "{duration}",
                                        getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                                    )
                                )
                                PaperLib.teleportAsync(player, home).thenAccept {
                                    if (it)
                                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TELEPORT)
                                    else
                                        tellError(io.bennyc.civilizations.settings.Localization.Warnings.FAILED_TELEPORT)
                                }
                                addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)

                            }
                        } else tell(io.bennyc.civilizations.settings.Localization.Warnings.TOWN_NOT_PUBLIC)
                    }
                return
            }
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.let { civilization ->
                checkNotNull(
                    civilization.home,
                    io.bennyc.civilizations.settings.Localization.Warnings.NULL_RESULT.replace("{item}", "${io.bennyc.civilizations.settings.Localization.CIVILIZATION} home")
                )
                checkBoolean(
                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                    io.bennyc.civilizations.settings.Localization.Warnings.COOLDOWN_WAIT.replace(
                        "{duration}",
                        getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                    )
                )
                PaperLib.teleportAsync(player, civilization.home!!).thenAccept {
                    if (it)
                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TELEPORT)
                    else
                        tellError(io.bennyc.civilizations.settings.Localization.Warnings.FAILED_TELEPORT)
                }
                addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
            }
        }
    }

    init {
        setDescription("Go to your civilization's home")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}