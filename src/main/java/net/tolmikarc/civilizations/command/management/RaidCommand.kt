/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import net.tolmikarc.civilizations.util.ClaimUtil.playersInCivOnline
import net.tolmikarc.civilizations.util.WarUtil.isInRaid
import net.tolmikarc.civilizations.util.WarUtil.isPlayerAtWar
import net.tolmikarc.civilizations.war.Raid
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.concurrent.TimeUnit

class RaidCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "raid") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                val enemyCiv = CivManager.getByName(args[0])
                checkNotNull(
                    enemyCiv,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
                )
                checkBoolean(!isInRaid(enemyCiv!!), Localization.Warnings.Raid.ALREADY_IN_RAID)
                checkBoolean(isPlayerAtWar(player, enemyCiv), Localization.Warnings.Raid.NO_WAR)
                checkBoolean(enemyCiv.claims.totalClaimCount > 0, Localization.Warnings.Raid.NO_LAND)
                checkBoolean(
                    !isLocationInCiv(player.location, enemyCiv),
                    Localization.Warnings.Raid.IN_ENEMY_LAND
                )
                if (Settings.RAID_RATIO_MAX_IN_RAID != -1) checkBoolean(
                    playersInCivOnline(enemyCiv) / playersInCivOnline(
                        this
                    ) >= Settings.RAID_RATIO_ONLINE_PLAYERS!!,
                    Localization.Warnings.Raid.NOT_ENOUGH_PLAYERS
                )
                val raid = Raid(enemyCiv, this)
                enemyCiv.raid = raid
                this.raid = raid
            }
        }
    }

    init {
        minArguments = 1
        setCooldown(Settings.RAID_COOLDOWN, TimeUnit.MINUTES)
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}