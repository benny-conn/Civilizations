/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.event.CivJoinEvent
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AAddCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "add") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        val addedPlayer = io.bennyc.civilizations.manager.PlayerManager.getByName(args[1])
        checkNotNull(
            addedPlayer,
            io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER)
        )
        civ?.apply {
            addedPlayer?.let {
                addCitizen(it)
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
                Common.callEvent(io.bennyc.civilizations.event.CivJoinEvent(this, player))
            }
        }
    }

    init {
        setDescription("Add a player to a Civilization")
        usage = "<civ> <player>"
        minArguments = 2
    }
}