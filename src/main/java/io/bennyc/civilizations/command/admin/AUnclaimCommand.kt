/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin


import io.bennyc.civilizations.util.ClaimUtil.getRegionFromLocation
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AUnclaimCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "unclaim") {
    override fun onCommand() {
        checkConsole()
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            val regionToRemove = getRegionFromLocation(player.location, this)
            checkNotNull(regionToRemove, io.bennyc.civilizations.settings.Localization.Warnings.Claim.NO_CLAIM)
            fun run() {
                claims.removeClaim(regionToRemove!!)
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
                Common.callEvent(
                    io.bennyc.civilizations.event.UnclaimEvent(
                        this,
                        regionToRemove,
                        player
                    )
                )
            }
            io.bennyc.civilizations.menu.ConfirmMenu(
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