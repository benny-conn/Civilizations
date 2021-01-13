/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil.calculateFormulaForCiv
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class SetWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "setwarp") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a civilization.")
            civPlayer.civilization?.apply {
                checkBoolean(canManageCiv(civPlayer, this), "You cannot manage this Civilization")
                checkBoolean(
                    isLocationInCiv(player.location, this),
                    "You must be in your Civilization to set a Warp."
                )
                val maxWarps = calculateFormulaForCiv(Settings.MAX_WARPS_FORMULA, this)
                checkBoolean(
                    warps.keys.size.toDouble() < maxWarps,
                    "You cannot have more than $maxWarps total warps."
                )
                addWarp(args[0], player.location)
                tellSuccess("{1}Set a Civilization Warp at your location with the name {2}" + args[0])
            }
        }
    }

    init {
        setDescription("Set a warp for your Civilization.")
        usage = "<name>"
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}