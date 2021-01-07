/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import io.papermc.lib.PaperLib
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class HomeCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "home") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (args.isNotEmpty()) {
                CivManager.getByName(args[0])
                    ?.let { if (it.claimToggleables.public) it.home?.let { home -> player.teleport(home) } else tell("&cTown not public") }
                return
            }
            checkNotNull(civPlayer.civilization, "You do not have a Civilization")
            civPlayer.civilization?.let { civilization ->
                checkNotNull(civilization.home, "Your Civilization does not have a home.")
                checkBoolean(
                    !hasCooldown(civPlayer.uuid, CooldownTask.CooldownType.TELEPORT),
                    "Please wait " + getCooldownRemaining(
                        civPlayer.uuid,
                        CooldownTask.CooldownType.TELEPORT
                    ) + " seconds before teleporting again."
                )
                PaperLib.teleportAsync(player, civilization.home!!).thenAccept {
                    if (it)
                        tellSuccess("Teleported to Civ Home!")
                    else
                        tellError("Failed to teleport to Civ Home!")
                }
                addCooldownTimer(civPlayer.uuid, CooldownTask.CooldownType.TELEPORT)
            }
        }
    }

    init {
        setDescription("Go to your civilization's home")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}