/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import io.papermc.lib.PaperLib
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.Colony
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.bukkit.Location
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ColonyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "colony") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.let { civ ->
                if (args[0].equals("list", ignoreCase = true)) {
                    val colonyIds: MutableList<String> = ArrayList()
                    for (colony in civ.claims.colonies) {
                        colonyIds.add(colony.id.toString())
                    }
                    tell("Colonies: " + Common.join(colonyIds, ", "))
                    return
                }
                val id = findNumber(
                    0,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
                )
                var location: Location? = null
                for (colony in civ.claims.colonies) {
                    if (colony.id == id) location = colony.warp
                }
                checkNotNull(location, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "colony"))
                checkBoolean(
                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                    Localization.Warnings.COOLDOWN_WAIT.replace(
                        "{duration}",
                        getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                    )
                )
                PaperLib.teleportAsync(player, location!!).thenAccept {
                    if (it)
                        tellSuccess(Localization.Notifications.SUCCESS_TELEPORT)
                    else
                        tellError(Localization.Warnings.FAILED_TELEPORT)
                }
                addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
            }
        }
    }

    override fun tabComplete(): List<String>? {
        val colonies: MutableList<String> = ArrayList()
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        if (civPlayer.civilization != null) {
            val civilization = civPlayer.civilization!!
            civilization.claims.colonies.forEach { colony: Colony -> colonies.add(colony.id.toString()) }
        }
        return if (args.size == 1) colonies else null
    }

    init {
        setDescription("Teleport to a Civilization Colony")
        usage = "<id # | list>"
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}