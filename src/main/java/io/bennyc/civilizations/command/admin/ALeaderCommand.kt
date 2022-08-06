/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.menu.ConfirmMenu
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ALeaderCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "leader") {
    override fun onCommand() {
        checkConsole()
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        val addedPlayer = io.bennyc.civilizations.manager.PlayerManager.getByName(args[1])
        checkNotNull(
            addedPlayer,
            io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER)
        )
        val newLeader = io.bennyc.civilizations.manager.PlayerManager.getByName(args[0])
        fun run() {
            checkNotNull(
                newLeader,
                io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER)
            )
            checkBoolean(civ!!.citizens.contains(newLeader), io.bennyc.civilizations.settings.Localization.Warnings.NOT_IN_CIV)
            civ.leader = newLeader
            tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
        }
        io.bennyc.civilizations.menu.ConfirmMenu("&4&lSet New Leader?", "WARNING: Irreversible", ::run)

    }


    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}