/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ALeaderCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "leader") {
    override fun onCommand() {
        checkConsole()
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        val addedPlayer = PlayerManager.getByName(args[1])
        checkNotNull(
            addedPlayer,
            Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
        )
        val newLeader = PlayerManager.getByName(args[0])
        fun run() {
            checkNotNull(
                newLeader,
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
            )
            checkBoolean(civ!!.citizens.contains(newLeader), Localization.Warnings.NOT_IN_CIV)
            civ.leader = newLeader
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
        }
        ConfirmMenu("&4&lSet New Leader?", "WARNING: Irreversible", ::run)

    }


    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}