/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.*

class WarpsCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warps") {
    override fun onCommand() {
        checkConsole()
        val civPlayer = CivPlayer.fromBukkitPlayer(player).run {
            checkNotNull(civilization, "You do not have a Civilization")
            val warpNames: List<String> = ArrayList(civilization!!.warps.keys)
            val warpNamesCombined = Common.join(warpNames, ", ")
            tell("${Settings.PRIMARY_COLOR}Warps: ${Settings.SECONDARY_COLOR}" + warpNamesCombined)
        }
    }

    init {
        setDescription("List your Civilization's Warps")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}