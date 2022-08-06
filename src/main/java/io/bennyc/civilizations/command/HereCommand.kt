/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.util.ClaimUtil
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class HereCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "here") {
    // TODO localize
    override fun onCommand() {
        checkConsole()
        val civ = ClaimUtil.getCivFromLocation(player.location)
        if (civ == null)
            tellError("There is no Civilization at your location")
        else
            tellSuccess("[${civ.name}]")
    }

    init {
        setDescription("Allows flight within your Civilization's Borders")
    }
}