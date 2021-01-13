/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class DenyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "deny") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).apply {
            checkNotNull(civilizationInvite, "You do not have any pending invites")
            tell("{2}Denied invite from the Civilization {1}" + civilizationInvite!!.name)
            civilizationInvite = null
        }
    }

    init {
        setDescription("Deny a Civilization's invite")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}