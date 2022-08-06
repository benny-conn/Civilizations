/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class WarpsCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warps") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).run {
            checkNotNull(civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            val warpNames: List<String> = ArrayList(civilization!!.warps.keys)
            val warpNamesCombined = Common.join(warpNames, ", ")
            tell("{1}Warps: {2}$warpNamesCombined")
        }
    }

    init {
        setDescription("List your Civilization's Warps")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}