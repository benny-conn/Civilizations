/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil.calculateFormulaForCiv
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ASetWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "setwarp") {
    override fun onCommand() {
        checkConsole()
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            checkBoolean(
                isLocationInCiv(player.location, this),
                Localization.Warnings.Claim.NO_CLAIM
            )
            val maxWarps = calculateFormulaForCiv(Settings.MAX_WARPS_FORMULA, this)
            checkBoolean(
                warps.keys.size.toDouble() < maxWarps,
                Localization.Warnings.MAXIMUM_WARPS.replace("{max}", maxWarps.toString())
            )
            addWarp(args[0], player.location)
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
        }
    }

    init {
        setDescription("Set a warp for a Civilization.")
        usage = "<civ> <name>"
        minArguments = 2
    }
}