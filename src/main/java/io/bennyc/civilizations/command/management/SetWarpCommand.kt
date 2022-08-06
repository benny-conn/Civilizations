/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker.canManageCiv
import io.bennyc.civilizations.util.CivUtil.calculateFormulaForCiv
import io.bennyc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class SetWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "setwarp") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                checkBoolean(
                    isLocationInCiv(player.location, this),
                    io.bennyc.civilizations.settings.Localization.Warnings.Claim.NO_CLAIM
                )
                val maxWarps = calculateFormulaForCiv(io.bennyc.civilizations.settings.Settings.MAX_WARPS_FORMULA, this)
                checkBoolean(
                    warps.keys.size.toDouble() < maxWarps,
                    io.bennyc.civilizations.settings.Localization.Warnings.MAXIMUM_WARPS.replace("{max}", maxWarps.toString())
                )
                addWarp(args[0], player.location)
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
            }
        }
    }

    init {
        setDescription("Set a warp for your Civilization.")
        usage = "<name>"
        minArguments = 1
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}