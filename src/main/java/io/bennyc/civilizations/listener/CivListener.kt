/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.listener

import io.bennyc.civilizations.event.CivEnterEvent
import io.bennyc.civilizations.event.CivLeaveEvent
import io.bennyc.civilizations.event.PlotEnterEvent
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.mineacademy.fo.Common
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.remain.Remain

class CivListener : Listener {

    @EventHandler
    fun onEnterCiv(event: io.bennyc.civilizations.event.CivEnterEvent) {
        val player = event.player
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val playersCivilization = civPlayer.civilization
        // does the player have a civilization that is raiding the new civ?
        if (event.civ.isBeingRaidedBy(playersCivilization) || event.civ.isBeingRaidedByAllyOf(playersCivilization)) {
            // if the player ratio isn't valid, he cannot participate in raid :(
            if (!event.civ.isPlayerToPlayerRatioValid()) {
                Common.tell(event.player, io.bennyc.civilizations.settings.Localization.Warnings.Raid.TOO_MANY_PLAYERS)
                return
            }
            // if the player is allowed in, then make sure he is a part of the involved players during the raid
            event.civ.raid?.addPlayerToRaid(player)
            civPlayer.lastLocationBeforeRaid = event.from
        }

        if (event.civ.isPlayerOutlaw(io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player))) {
            // if the settings say no outlaws in, make sure no outlaws come in
            if (io.bennyc.civilizations.settings.Settings.OUTLAW_ENTER_DISABLED) {
                Messenger.warn(player, io.bennyc.civilizations.settings.Localization.Warnings.OUTLAW_ENTER)
                event.isCancelled = true
                return
            }
            // if the settings say outlaws can't do anything, make sure the player knows that
            if (io.bennyc.civilizations.settings.Settings.OUTLAW_PERMISSIONS_DISABLED)
                Messenger.warn(
                    player,
                    io.bennyc.civilizations.settings.Localization.Warnings.OUTLAW_ACTIONS
                )
        }
        // when a player has flight enabled and walks in, make that player fly
        if (event.civ.citizens.contains(civPlayer) && civPlayer.flying) {
            player.allowFlight = true
            player.isFlying = true
        }
        // FINALLY make sure the player knows hes entering a new civ
        when (io.bennyc.civilizations.settings.Settings.NOTICE_TYPE) {
            1 -> Remain.sendActionBar(
                event.player,
                io.bennyc.civilizations.settings.Localization.Notifications.ENTER_CIV.replace(
                    "{civ}",
                    event.civ.name!!
                ) + (if (event.civ.toggleables.pvp) " &4&l[PVP]" else "")
            )
            2 -> Remain.sendTitle(
                player,
                io.bennyc.civilizations.settings.Localization.Notifications.ENTER_CIV.replace(
                    "{civ}",
                    event.civ.name!!
                ) + (if (event.civ.toggleables.pvp) " &4&l[PVP]" else ""),
                "{1}Power: {2}${event.civ.power}"
            )
        }
    }

    @EventHandler
    fun onLeaveCiv(event: io.bennyc.civilizations.event.CivLeaveEvent) {
        val player = event.player
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        // let the player know if we are leaving the civ
        when (io.bennyc.civilizations.settings.Settings.NOTICE_TYPE) {
            1 -> Remain.sendActionBar(
                player,
                io.bennyc.civilizations.settings.Localization.Notifications.LEAVING_CIV.replace("{civ}", event.civ.name!!)
            )
            2 -> Remain.sendTitle(
                player,
                io.bennyc.civilizations.settings.Localization.Notifications.LEAVING_CIV.replace("{civ}", event.civ.name!!),
                ""
            )
        }
        // stop the player from flying if he leaves his own civ
        if (civPlayer.flying) {
            player.isFlying = false
            player.allowFlight = false
        }
    }

    @EventHandler
    fun onEnterPlot(event: io.bennyc.civilizations.event.PlotEnterEvent) {
        val plotOwner = event.plot.owner
        Remain.sendActionBar(
            event.player,
            if (event.plot.forSale)
                "{1}Plot: {2}" + event.plot.price + (if (event.plot.toggleables.pvp) " &4&l[PVP]" else "")
            else
                "{1}Plot: {2}" +
                        if (plotOwner.uuid != event.plot.civ.leader?.uuid) plotOwner.playerName
                        else "Unowned" + (if (event.plot.toggleables.pvp) " &4&l[PVP]" else "")
        )
    }


}