/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class FlyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "fly") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).apply {
            checkNotNull(civilization, Localization.Warnings.NO_CIV)
            flying = !flying
            tellSuccess(Localization.Notifications.FLIGHT.replace("{value}", flying.toString()))
            if (isLocationInCiv(player.location, civilization!!)) {
                player.allowFlight = flying
                player.isFlying = flying
            }
        }
    }

    init {
        setDescription("Allows flight within your Civilization's Borders")
    }
}