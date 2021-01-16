/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import io.papermc.lib.PaperLib
import net.tolmikarc.civilizations.PermissionChecker.isAdmin
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class HomeCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "home|tp") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (args.isNotEmpty()) {
                CivManager.getByName(args[0])
                    ?.let { civ ->
                        if (isAdmin(civPlayer) && civ.home != null) {
                            PaperLib.teleportAsync(player, civ.home!!).thenAccept {
                                if (it)
                                    tellSuccess("{1}Teleported to Civ Home!")
                                else
                                    tellError("Failed to teleport to Civ Home!")
                            }
                        }
                        if (civ.toggleables.public) {
                            civ.home?.let { home ->
                                checkBoolean(
                                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                                    Localization.Warnings.COOLDOWN_WAIT.replace(
                                        "{duration}",
                                        getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                                    )
                                )
                                PaperLib.teleportAsync(player, home).thenAccept {
                                    if (it)
                                        tellSuccess("{1}Teleported to Civ Home!")
                                    else
                                        tellError(Localization.Warnings.FAILED_TELEPORT)
                                }
                                addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)

                            }
                        } else tell(Localization.Warnings.TOWN_NOT_PUBLIC)
                    }
                return
            }
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.let { civilization ->
                checkNotNull(
                    civilization.home,
                    Localization.Warnings.NULL_RESULT.replace("{item}", "${Localization.CIVILIZATION} home")
                )
                checkBoolean(
                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                    Localization.Warnings.COOLDOWN_WAIT.replace(
                        "{duration}",
                        getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                    )
                )
                PaperLib.teleportAsync(player, civilization.home!!).thenAccept {
                    if (it)
                        tellSuccess("{1}Teleported to Civ Home!")
                    else
                        tellError(Localization.Warnings.FAILED_TELEPORT)
                }
                addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
            }
        }
    }

    init {
        setDescription("Go to your civilization's home")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}