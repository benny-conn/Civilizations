/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.listener

import net.tolmikarc.civilizations.event.war.RaidBeginEvent
import net.tolmikarc.civilizations.event.war.RaidEndEvent
import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.packet.NameTag
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.mineacademy.fo.Common

class WarListener : Listener {

    @EventHandler
    fun onRaidBegin(event: RaidBeginEvent) {
        val raid = event.raid
        val attacker = event.attacker
        val defender = event.defender
        for (player in Bukkit.getOnlinePlayers()) {
            CivPlayer.fromBukkitPlayer(player).let {
                if (defender.citizens.contains(it)) {
                    Common.tell(player, "${Settings.PRIMARY_COLOR}" + raid.timeLeft / 60 + " Minutes left!")
                    raid.addPlayerToRaid(player)
                }
                if (attacker.citizens.contains(it)) {
                    Common.tell(player, "${Settings.PRIMARY_COLOR}" + raid.timeLeft / 60 + " Minutes left!")
                }
            }
        }
    }

    @EventHandler
    fun onRaidEnd(event: RaidEndEvent) {
        val raid = event.raid
        val attacker = event.attacker
        val defender = event.defender
        for (player in Bukkit.getOnlinePlayers()) {
            CivPlayer.fromBukkitPlayer(player).let {
                if (defender.citizens.contains(it)) Common.tell(
                    player,
                    "${Settings.PRIMARY_COLOR}Raid over!"
                )
                if (attacker.citizens.contains(it)) Common.tell(
                    player,
                    "${Settings.PRIMARY_COLOR}Raid over!"
                )
            }
            for (civPlayer in raid.playersInvolved.keys) {
                civPlayer.playerName?.let {
                    Bukkit.getPlayer(civPlayer.playerUUID)?.let { bukkitPlayer -> NameTag.remove(bukkitPlayer) }
                }
            }
            defender.raid = null
            attacker.raid = null
        }
    }


}