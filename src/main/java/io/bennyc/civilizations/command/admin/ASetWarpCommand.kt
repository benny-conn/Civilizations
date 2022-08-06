/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.util.CivUtil.calculateFormulaForCiv
import io.bennyc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ASetWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "setwarp") {
    override fun onCommand() {
        checkConsole()
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
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

    init {
        setDescription("Set a warp for a Civilization.")
        usage = "<civ> <name>"
        minArguments = 2
    }
}