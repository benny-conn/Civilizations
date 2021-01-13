/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.event.UnclaimEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.util.ClaimUtil.getRegionFromLocation
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AUnclaimCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "unclaim") {
    override fun onCommand() {
        checkConsole()
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
            val regionToRemove = getRegionFromLocation(player.location, this)
            checkNotNull(regionToRemove, "There is no region at your location")
            fun run() {
                claims.removeClaim(regionToRemove!!)
                tellSuccess("{3}Removed region successfully")
                Common.callEvent(
                    UnclaimEvent(
                        this,
                        regionToRemove,
                        player
                    )
                )
            }
            ConfirmMenu(
                "&4Remove Region Here?",
                "Use \"/civ claim visualize here\" to see this claim before deleting it.",
                ::run
            ).displayTo(player)

        }
    }

    init {
        usage = "<civ>"
        setDescription("Remove region at your location from a Civilization's claims")
    }
}