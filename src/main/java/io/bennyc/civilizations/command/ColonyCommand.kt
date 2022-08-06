/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.papermc.lib.PaperLib
import io.bennyc.civilizations.model.Colony
import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import io.bennyc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import io.bennyc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.bukkit.Location
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ColonyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "colony") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
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
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.NUMBER)
                )
                var location: Location? = null
                for (colony in civ.claims.colonies) {
                    if (colony.id == id) location = colony.warp
                }
                checkNotNull(location, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "colony"))
                checkBoolean(
                    !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                    io.bennyc.civilizations.settings.Localization.Warnings.COOLDOWN_WAIT.replace(
                        "{duration}",
                        getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                    )
                )
                PaperLib.teleportAsync(player, location!!).thenAccept {
                    if (it)
                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TELEPORT)
                    else
                        tellError(io.bennyc.civilizations.settings.Localization.Warnings.FAILED_TELEPORT)
                }
                addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
            }
        }
    }

    override fun tabComplete(): List<String>? {
        val colonies: MutableList<String> = ArrayList()
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
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
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}