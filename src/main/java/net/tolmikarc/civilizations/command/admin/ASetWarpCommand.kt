/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil.calculateFormulaForCiv
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ASetWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "setwarp") {
    override fun onCommand() {
        checkConsole()
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
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
            tellSuccess("${Settings.PRIMARY_COLOR}Set a Civilization Warp at your location with the name ${Settings.SECONDARY_COLOR}" + args[0])
        }
    }

    init {
        setDescription("Set a warp for a Civilization.")
        usage = "<civ> <name>"
        minArguments = 2
    }
}