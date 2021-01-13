/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ChatCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "chat") {
    override fun onCommand() {
        checkConsole()
        val civ = PlayerManager.fromBukkitPlayer(player).civilization
        checkNotNull(civ, "You must have a civ to use this command")
        if (civ!!.channel.players.contains(player)) {
            civ.channel.players.remove(player)
            tellSuccess("{1}Left Civ Chat")
        } else {
            civ.channel.players.add(player)
            tellSuccess("{1}Entered Civ Chat")
        }
    }


    init {
        setDescription("Set")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}