/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.event.CivJoinEvent
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AcceptCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "accept") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkBoolean(civPlayer.civilization == null, io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_JOIN_CIV)
            checkNotNull(civPlayer.civilizationInvite, io.bennyc.civilizations.settings.Localization.Warnings.NULL_RESULT.replace("{item}", "invites"))
            civPlayer.civilizationInvite?.apply {
                addCitizen(civPlayer)
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.ACCEPTED_INVITE.replace("{civ}", this.name!!))
                tellInfo(io.bennyc.civilizations.settings.Localization.Notifications.INFO)
                Common.callEvent(io.bennyc.civilizations.event.CivJoinEvent(this, player))
            }
        }

    }

    init {
        setDescription("Accept a Civilization's invite")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}