/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class FlyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "fly") {
    override fun onCommand() {
        checkConsole()
        player.allowFlight = true
        PlayerManager.fromBukkitPlayer(player).apply {
            checkNotNull(civilization, Localization.Warnings.NO_CIV)
            flying = !flying
            tellSuccess("{1}Enabled flight while you are in your Civilization.")
            if (isLocationInCiv(player.location, civilization!!)) player.isFlying = flying
        }
    }

    init {
        setDescription("Allows flight within your Civilization's Borders")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}