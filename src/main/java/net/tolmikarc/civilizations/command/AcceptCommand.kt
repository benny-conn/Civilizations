/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.api.event.CivJoinEvent
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AcceptCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "accept") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilizationInvite, Localization.Warnings.NULL_RESULT.replace("{item}", "invites"))
            civPlayer.civilizationInvite?.apply {
                this.addCitizen(civPlayer)
                civPlayer.civilization = this
                civPlayer.civilizationInvite = null
                tellSuccess(Localization.Notifications.ACCEPTED_INVITE.replace("{civ}", this.name!!))
                tellInfo(Localization.Notifications.INFO)
                Common.callEvent(CivJoinEvent(this, player))
            }
        }

    }

    init {
        setDescription("Accept a Civilization's invite")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}