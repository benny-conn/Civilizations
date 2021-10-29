/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.event.CivJoinEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AAddCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "add") {
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
                addCitizen(it)
                tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                Common.callEvent(CivJoinEvent(this, player))
            }
        }
    }

    init {
        setDescription("Add a player to a Civilization")
        usage = "<civ> <player>"
        minArguments = 2
    }
}