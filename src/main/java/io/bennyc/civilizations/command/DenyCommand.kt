/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class DenyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "deny") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).apply {
            checkNotNull(civilizationInvite, io.bennyc.civilizations.settings.Localization.Warnings.NULL_RESULT.replace("{item}", "invites"))
            tell(io.bennyc.civilizations.settings.Localization.Notifications.DENIED_INVITE.replace("{civ}", civilization?.name!!))
            civilizationInvite = null
        }
    }

    init {
        setDescription("Deny a Civilization's invite")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}