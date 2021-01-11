/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.listener

import net.tolmikarc.civilizations.event.CivEnterEvent
import net.tolmikarc.civilizations.event.CivLeaveEvent
import net.tolmikarc.civilizations.event.PlotEnterEvent
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.WarUtil
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.mineacademy.fo.Common
import org.mineacademy.fo.remain.Remain

class CivListener : Listener {

    @EventHandler
    fun onEnterCiv(event: CivEnterEvent) {
        val player = event.player
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val playersCiv = civPlayer.civilization
        // does the player have a civilization that is raiding the new civ?
        if (WarUtil.isBeingRaided(event.civ, playersCiv) || WarUtil.isBeingRaidedByAlly(event.civ, playersCiv)) {
            // if the player ratio isn't valid, he cannot participate in raid :(
            if (!WarUtil.isPlayerToPlayerRatioValid(event.civ, playersCiv)) {
                Common.tell(event.player, "&cThere are too many players in this raid for you to participate")
                return
            }
            // if the player is allowed in, then make sure he is a part of the involved players during the raid
            event.civ.raid?.addPlayerToRaid(player)
        }

        if (CivUtil.isPlayerOutlaw(PlayerManager.fromBukkitPlayer(player), event.civ)) {
            // if the settings say no outlaws in, make sure no outlaws come in
            if (Settings.OUTLAW_ENTER_DISABLED) {
                Common.tell(player, "&4&lWARNING: &cYou are an outlaw in this town and cannot enter.")
                event.isCancelled = true
                return
            }
            // if the settings say outlaws can't do anything, make sure the player knows that
            if (Settings.OUTLAW_PERMISSIONS_DISABLED)
                Common.tell(
                    player,
                    "&4&lWARNING: &cYou are an outlaw in this town and cannot do anything."
                )
        }
        // when a player has flight enabled and walks in, make that player fly
        if (event.civ.citizens.contains(civPlayer) && civPlayer.flying) {
            player.allowFlight = true
            player.isFlying = true
        }
        // FINALLY make sure the player knows hes entering a new civ
        when (Settings.NOTICE_TYPE) {
            1 -> Remain.sendActionBar(
                event.player,
                "${Settings.PRIMARY_COLOR}Now entering ${Settings.SECONDARY_COLOR}" + event.civ.name + (if (event.civ.toggleables.pvp) " &4&l[PVP]" else "")
            )
            2 -> Remain.sendTitle(
                player,
                "${Settings.PRIMARY_COLOR}Entering ${Settings.SECONDARY_COLOR}${event.civ.name}" + (if (event.civ.toggleables.pvp) " &4&l[PVP]" else ""),
                "${Settings.PRIMARY_COLOR}Power: ${Settings.SECONDARY_COLOR}${event.civ.power}"
            )
        }
    }

    @EventHandler
    fun onLeaveCiv(event: CivLeaveEvent) {
        val player = event.player
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        // let the player know if we are leaving the civ
        when (Settings.NOTICE_TYPE) {
            1 -> Remain.sendActionBar(
                player,
                "${Settings.PRIMARY_COLOR}Now Leaving ${Settings.SECONDARY_COLOR}" + event.civ.name
            )
            2 -> Remain.sendTitle(
                player,
                "${Settings.PRIMARY_COLOR}Leaving ${Settings.SECONDARY_COLOR}${event.civ.name}",
                ""
            )
        }
        // stop the player from flying if he leaves his own civ
        if (event.civ.citizens.contains(civPlayer) && civPlayer.flying) {
            player.isFlying = false
        }
    }

    @EventHandler
    fun onEnterPlot(event: PlotEnterEvent) {
        val plotOwner = event.plot.owner
        Remain.sendActionBar(
            event.player,
            if (event.plot.forSale)
                "${Settings.PRIMARY_COLOR}Plot: ${Settings.SECONDARY_COLOR}" + event.plot.price + (if (event.plot.claimToggleables.pvp) " &4&l[PVP]" else "")
            else
                "${Settings.PRIMARY_COLOR}Plot: ${Settings.SECONDARY_COLOR}" +
                        if (plotOwner.uuid != event.plot.civ.leader?.uuid) plotOwner.playerName
                        else "Unowned" + (if (event.plot.claimToggleables.pvp) " &4&l[PVP]" else "")
        )
    }


}