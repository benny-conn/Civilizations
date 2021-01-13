/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import io.papermc.lib.PaperLib
import net.tolmikarc.civilizations.PermissionChecker.isAdmin
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
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
                                    tellSuccess("${Settings.PRIMARY_COLOR}Teleported to Civ Home!")
                                else
                                    tellError("Failed to teleport to Civ Home!")
                            }
                        }
                        if (civ.toggleables.public) {
                            civ.home?.let { home ->
                                checkBoolean(
                                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                                    "Please wait " + getCooldownRemaining(
                                        civPlayer,
                                        CooldownTask.CooldownType.TELEPORT
                                    ) + " seconds before teleporting again."
                                )
                                PaperLib.teleportAsync(player, home).thenAccept {
                                    if (it)
                                        tellSuccess("${Settings.PRIMARY_COLOR}Teleported to Civ Home!")
                                    else
                                        tellError("Failed to teleport to Civ Home!")
                                }
                                addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)

                            }
                        } else tell("&cTown not public")
                    }
                return
            }
            checkNotNull(civPlayer.civilization, "You do not have a Civilization")
            civPlayer.civilization?.let { civilization ->
                checkNotNull(civilization.home, "Your Civilization does not have a home.")
                checkBoolean(
                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                    "Please wait " + getCooldownRemaining(
                        civPlayer,
                        CooldownTask.CooldownType.TELEPORT
                    ) + " seconds before teleporting again."
                )
                PaperLib.teleportAsync(player, civilization.home!!).thenAccept {
                    if (it)
                        tellSuccess("${Settings.PRIMARY_COLOR}Teleported to Civ Home!")
                    else
                        tellError("Failed to teleport to Civ Home!")
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