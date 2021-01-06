/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.event.civ.JoinCivEvent
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AcceptCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "accept") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilizationInvite, "You do not have any pending invites")
            civPlayer.civilizationInvite?.apply {
                this.addCitizen(civPlayer)
                civPlayer.civilization = this
                civPlayer.civilizationInvite = null
                tell("${Settings.SECONDARY_COLOR}Accepted invite from the Civilization ${Settings.PRIMARY_COLOR}" + civPlayer.civilization!!.name)
                tell("${Settings.SECONDARY_COLOR}Type " + "${Settings.PRIMARY_COLOR}/civ ? ${Settings.SECONDARY_COLOR}for a list of Civilizations commands.")
                Common.callEvent(JoinCivEvent(this, player))
            }
        }

    }

    init {
        setDescription("Accept a Civilization's invite")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}