/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import net.tolmikarc.civilizations.util.ClaimUtil.playersInCivOnline
import net.tolmikarc.civilizations.util.WarUtil.isInRaid
import net.tolmikarc.civilizations.util.WarUtil.isPlayerAtWar
import net.tolmikarc.civilizations.war.Raid
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class RaidCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "raid") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You must have a civilization to Raid another.")
            civPlayer.civilization?.apply {
                val enemyCiv = CivManager.getByName(args[0])
                checkNotNull(enemyCiv, "Please specify a valid Civilization")
                checkBoolean(!isInRaid(enemyCiv!!), "This civilization is already in a Raid")
                checkBoolean(isPlayerAtWar(player, enemyCiv), "You must first be at war to raid an enemy")
                checkBoolean(enemyCiv.claims.totalClaimCount > 0, "The enemy does not have any land")
                checkBoolean(
                    !isLocationInCiv(player.location, enemyCiv),
                    "You must not be in the enemy territory when you begin a raid"
                )
                if (Settings.RAID_RATIO_MAX_IN_RAID != -1) checkBoolean(
                    playersInCivOnline(enemyCiv) / playersInCivOnline(
                        this
                    ) >= Settings.RAID_RATIO_ONLINE_PLAYERS!!,
                    "There are not enough enemy players online to start a raid."
                )
                checkBoolean(
                    !hasCooldown(this, CooldownTask.CooldownType.RAID),
                    "You have to wait " + getCooldownRemaining(
                        this,
                        CooldownTask.CooldownType.RAID
                    ) / 60 + " minutes to begin another raid"
                )
                val raid = Raid(enemyCiv, this)
                enemyCiv.raid = raid
                this.raid = raid
            }
        }
    }

    init {
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}