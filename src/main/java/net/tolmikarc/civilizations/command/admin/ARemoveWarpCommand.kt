/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ARemoveWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "removewarp") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            checkBoolean(
                warps.containsKey(args[1]),
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp")
            )
            removeWarp(args[1])
            tellSuccess("{1}Set a Civilization Warp at your location with the name {2}" + args[0])
        }
    }

    init {
        setDescription("Set a warp for a Civilization.")
        usage = "<civ> <name>"
        minArguments = 2
    }
}