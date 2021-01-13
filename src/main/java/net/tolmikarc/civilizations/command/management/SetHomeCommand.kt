/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class SetHomeCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "sethome") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a civilization.")
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    "You must be the leader or official of your Civilization to set the home"
                )
                checkBoolean(
                    isLocationInCiv(player.location, this),
                    "You must be in your Civilization to set the home."
                )
                home = player.location
                tellSuccess("${Settings.SECONDARY_COLOR}Set the Civilization home location")
            }
        }
    }

    init {
        setDescription("Set your Civilizations home location.")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}