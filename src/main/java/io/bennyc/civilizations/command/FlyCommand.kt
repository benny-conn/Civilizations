/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class FlyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "fly") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).apply {
            checkNotNull(civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            flying = !flying
            tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.FLIGHT.replace("{value}", flying.toString()))
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