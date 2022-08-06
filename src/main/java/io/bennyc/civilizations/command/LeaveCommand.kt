/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.menu.ConfirmMenu
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class LeaveCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "leave") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            val civilization = civPlayer.civilization
            checkBoolean(
                civilization!!.leader != civPlayer,
                io.bennyc.civilizations.settings.Localization.Warnings.LEAVE_CIV_AS_LEADER
            )

            fun run() {
                civilization.removeCitizen(civPlayer)
                io.bennyc.civilizations.manager.PlayerManager.saveAsync(civPlayer)
                io.bennyc.civilizations.manager.CivManager.saveAsync(civilization)
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
            }

            io.bennyc.civilizations.menu.ConfirmMenu("&4Leave Civilization?", "You cannot undo this decision.", ::run).displayTo(player)
        }
    }

    init {
        setDescription("Leave your current Civilization")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}