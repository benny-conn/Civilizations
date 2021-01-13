/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AWarpsCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warps") {
    override fun onCommand() {
        checkConsole()
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        val warpNames: List<String> = ArrayList(civ!!.warps.keys)
        val warpNamesCombined = Common.join(warpNames, ", ")
        tell("{1}Warps: {2}" + warpNamesCombined)
    }

    init {
        setDescription("List your Civilization's Warps")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}