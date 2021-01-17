/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class DenyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "deny") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).apply {
            checkNotNull(civilizationInvite, Localization.Warnings.NULL_RESULT.replace("{item}", "invites"))
            tell(Localization.Notifications.DENIED_INVITE.replace("{civ}", civilization?.name!!))
            civilizationInvite = null
        }
    }

    init {
        setDescription("Deny a Civilization's invite")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}