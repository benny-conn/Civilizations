/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.util.ClaimUtil.isLocationInCiv
import io.bennyc.civilizations.util.ClaimUtil.playersInCivOnline
import io.bennyc.civilizations.war.Raid
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.concurrent.TimeUnit

class RaidCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "raid") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                val enemyCivilization = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
                checkNotNull(
                    enemyCivilization,
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION)
                )
                checkBoolean(!enemyCivilization!!.isInRaid(), io.bennyc.civilizations.settings.Localization.Warnings.Raid.ALREADY_IN_RAID)
                checkBoolean(enemyCivilization.isAtWarWith(player), io.bennyc.civilizations.settings.Localization.Warnings.Raid.NO_WAR)
                checkBoolean(enemyCivilization.claims.totalClaimCount > 0, io.bennyc.civilizations.settings.Localization.Warnings.Raid.NO_LAND)
                checkBoolean(
                    !isLocationInCiv(player.location, enemyCivilization),
                    io.bennyc.civilizations.settings.Localization.Warnings.Raid.IN_ENEMY_LAND
                )
                if (io.bennyc.civilizations.settings.Settings.RAID_RATIO_MAX_IN_RAID != -1) checkBoolean(
                    playersInCivOnline(enemyCivilization) / playersInCivOnline(
                        this
                    ) >= io.bennyc.civilizations.settings.Settings.RAID_RATIO_ONLINE_PLAYERS!!,
                    io.bennyc.civilizations.settings.Localization.Warnings.Raid.NOT_ENOUGH_PLAYERS
                )
                val raid = Raid(enemyCivilization, this)
                enemyCivilization.raid = raid
                this.raid = raid
                setCooldown(io.bennyc.civilizations.settings.Settings.RAID_COOLDOWN, TimeUnit.MINUTES)
            }
        }
    }

    init {
        minArguments = 1
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}