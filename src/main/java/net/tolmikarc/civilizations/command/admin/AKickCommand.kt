/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AKickCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "kick") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        val addedPlayer = PlayerManager.getByName(args[1])
        checkNotNull(
            addedPlayer,
            Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
        )
        civ?.apply {
            addedPlayer?.let {
                removeCitizen(it)
                tellSuccess("{1}Successfully removed {2}${it.playerName} {1}from {2}$name")
            }
        }
    }

    init {
        setDescription("Kick a player from a Civilization")
        usage = "<civ> <player>"
        minArguments = 2
    }
}