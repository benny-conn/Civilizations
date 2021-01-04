/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.war

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.packet.NameTag
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.mineacademy.fo.Common
import org.mineacademy.fo.model.Countdown
import java.util.*
import kotlin.collections.ArrayList

class Raid(val civBeingRaided: Civilization, val civRaiding: Civilization) : Countdown(Settings.RAID_LENGTH!!) {

    // map of players and their lives
    val playersInvolved: MutableMap<CivPlayer, Int> = HashMap()

    fun addPlayerToRaid(player: Player) {
        CivPlayer.fromBukkitPlayer(player).let {
            playersInvolved.putIfAbsent(it, Settings.RAID_LIVES)

            if (Common.doesPluginExist("protocollib")) {
                val onlinePlayersFromCivs: MutableList<Player> = ArrayList()
                for (p in Bukkit.getOnlinePlayers()) {
                    if (civBeingRaided.citizens.contains(CivPlayer.fromBukkitPlayer(p)) || civRaiding.citizens.contains(
                            CivPlayer.fromBukkitPlayer(p)
                        )
                    )
                        onlinePlayersFromCivs.add(p)
                }
                NameTag.of("&c" + player.displayName).applyTo(player, onlinePlayersFromCivs)
            }
        }
    }


    override fun onStart() {
        for (player in Bukkit.getOnlinePlayers()) {
            CivPlayer.fromBukkitPlayer(player).let {
                if (civBeingRaided.citizens.contains(it)) {
                    Common.tell(player, "${Settings.PRIMARY_COLOR}" + timeLeft / 60 + " Minutes left!")
                    addPlayerToRaid(player)
                }
                if (civRaiding.citizens.contains(it)) {
                    Common.tell(player, "${Settings.PRIMARY_COLOR}" + timeLeft / 60 + " Minutes left!")
                }
            }
        }
    }

    override fun onTick() {
        if (timeLeft % 120 == 0)
            for (player in Bukkit.getOnlinePlayers()) {
                CivPlayer.fromBukkitPlayer(player).let {
                    if (civBeingRaided.citizens.contains(it)) Common.tell(
                        player,
                        "${Settings.PRIMARY_COLOR}" + timeLeft / 60 + " Minutes left!"
                    )
                    if (civBeingRaided.citizens.contains(it)) Common.tell(
                        player,
                        "${Settings.PRIMARY_COLOR}" + timeLeft / 60 + " Minutes left!"
                    )
                }
            }
    }

    override fun onEnd() {
        for (player in Bukkit.getOnlinePlayers()) {
            CivPlayer.fromBukkitPlayer(player).let {
                if (civBeingRaided.citizens.contains(it)) Common.tell(
                    player,
                    "${Settings.PRIMARY_COLOR}Raid over!"
                )
                if (civRaiding.citizens.contains(it)) Common.tell(
                    player,
                    "${Settings.PRIMARY_COLOR}Raid over!"
                )
            }
            for (civPlayer in playersInvolved.keys) {
                civPlayer.playerName?.let {
                    Bukkit.getPlayer(civPlayer.playerUUID)?.let { bukkitPlayer -> NameTag.remove(bukkitPlayer) }
                }
            }
            civBeingRaided.raid = null
            civRaiding.raid = null
        }
    }


    init {
        launch()
    }
}