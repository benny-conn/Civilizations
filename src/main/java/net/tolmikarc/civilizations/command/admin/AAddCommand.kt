/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.event.CivJoinEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AAddCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "add") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        val addedPlayer = PlayerManager.getByName(args[1])
        checkNotNull(addedPlayer, "Please specify a valid player")
        civ?.apply {
            addedPlayer?.let {
                addCitizen(it)
                tellSuccess("${Settings.PRIMARY_COLOR}Successfully added ${Settings.SECONDARY_COLOR}${it.playerName} ${Settings.PRIMARY_COLOR}to ${Settings.SECONDARY_COLOR}$name")
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