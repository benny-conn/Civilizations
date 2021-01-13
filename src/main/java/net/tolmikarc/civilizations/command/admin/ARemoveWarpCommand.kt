/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ARemoveWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "removewarp") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
            checkBoolean(warps.containsKey(args[1]), "Please specify a valid warp")
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