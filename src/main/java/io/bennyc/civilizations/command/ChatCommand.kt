/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ChatCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "chat") {
    override fun onCommand() {
        checkConsole()
        val civ = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).civilization
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
        if (civ!!.channel.players.contains(player)) {
            civ.channel.players.remove(player)
            tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.LEFT_CHAT)
        } else {
            civ.channel.players.add(player)
            tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.ENTERED_CHAT)
        }
    }


    init {
        setDescription("Set")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}