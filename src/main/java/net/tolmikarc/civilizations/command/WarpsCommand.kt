/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.*

class WarpsCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warps") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).run {
            checkNotNull(civilization, Localization.Warnings.NO_CIV)
            val warpNames: List<String> = ArrayList(civilization!!.warps.keys)
            val warpNamesCombined = Common.join(warpNames, ", ")
            tell("{1}Warps: {2}$warpNamesCombined")
        }
    }

    init {
        setDescription("List your Civilization's Warps")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}