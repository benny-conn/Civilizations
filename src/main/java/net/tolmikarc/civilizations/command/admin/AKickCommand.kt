/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.event.CivLeaveEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AKickCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "kick") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        val addedPlayer = PlayerManager.getByName(args[1])
        checkNotNull(addedPlayer, "Please specify a valid player")
        civ?.apply {
            addedPlayer?.let {
                removeCitizen(it)
                tellSuccess("{1}Successfully removed {2}${it.playerName} {1}from {2}$name")
                Common.callEvent(CivLeaveEvent(this, player))
            }
        }
    }

    init {
        setDescription("Kick a player from a Civilization")
        usage = "<civ> <player>"
        minArguments = 2
    }
}