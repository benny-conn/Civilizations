/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.util.ClaimUtil.getRegionFromLocation
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class UnclaimCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "unclaim") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
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
                    "&4Remove claim Here?",
                    "Use \"/civ claim visualize here\" to see this claim before deleting it.",
                    ::run
                ).displayTo(player)
            }
        }
    }

    init {
        setDescription("Remove claim at your location from your Civilization's claims")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}