/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.war

import net.tolmikarc.civilizations.NameTag
import net.tolmikarc.civilizations.event.PlayerJoinRaidEvent
import net.tolmikarc.civilizations.event.RaidBeginEvent
import net.tolmikarc.civilizations.event.RaidEndEvent
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.util.ClaimUtil
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.mineacademy.fo.Common
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.model.Countdown
import org.mineacademy.fo.remain.Remain
import java.util.*

class Raid(val civBeingRaided: Civ, val civRaiding: Civ) : Countdown(Settings.RAID_LENGTH) {

    // map of players and their lives
    val playersInvolved: MutableMap<CPlayer, Int> = HashMap()

    fun addPlayerToRaid(player: Player) {

        Remain.sendBossbarTimed(
            player,
            "${ChatColor.RED}${ChatColor.BOLD}Raid of ${ChatColor.DARK_RED}${ChatColor.BOLD}${civBeingRaided.name}",
            timeLeft
        )

        PlayerManager.fromBukkitPlayer(player).let {

            playersInvolved.putIfAbsent(it, Settings.RAID_LIVES)

            if (playersInvolved[it] == 0) return

            if (playersInvolved[it] == Settings.RAID_LIVES) Common.callEvent(PlayerJoinRaidEvent(this, player))

            if (Common.doesPluginExist("ProtocolLib")) {
                val onlinePlayersFromOppositeCiv = mutableListOf<Player>()
                for (p in Bukkit.getOnlinePlayers()) {
                    if (civRaiding == it.civilization)
                        if (PlayerManager.fromBukkitPlayer(p).civilization == civBeingRaided)
                            onlinePlayersFromOppositeCiv.add(p)
                    if (civBeingRaided == it.civilization)
                        if (PlayerManager.fromBukkitPlayer(p).civilization == civRaiding)
                            onlinePlayersFromOppositeCiv.add(p)
                }
                NameTag.of("&c" + player.displayName).applyTo(player, onlinePlayersFromOppositeCiv)

            }
        }
    }


    override fun onStart() {
        for (player in Bukkit.getOnlinePlayers()) {
            PlayerManager.fromBukkitPlayer(player).let {
                if (civBeingRaided.citizens.contains(it)) {
                    Messenger.warn(player, "Raid begun on ${civBeingRaided.name} by ${civRaiding.name}")
                    Messenger.warn(
                        player,
                        "${timeLeft / 60} Minutes left!"
                    )
                    if (ClaimUtil.isLocationInCiv(player.location, civBeingRaided))
                        addPlayerToRaid(player)
                }
                if (civRaiding.citizens.contains(it)) {
                    Messenger.warn(player, "Raid begun on ${civBeingRaided.name} by ${civRaiding.name}")
                    Messenger.warn(
                        player,
                        "${timeLeft / 60} Minutes left!"
                    )
                }
            }
        }
        Common.callEvent(RaidBeginEvent(this))
    }

    override fun onTick() {
        if (timeLeft % 120 == 0)
            for (player in Bukkit.getOnlinePlayers()) {
                PlayerManager.fromBukkitPlayer(player).let {
                    if (civBeingRaided.citizens.contains(it)) Messenger.warn(
                        player,
                        "${timeLeft / 60} Minutes left!"
                    )
                    if (civRaiding.citizens.contains(it)) Messenger.warn(
                        player,
                        "${timeLeft / 60} Minutes left!"
                    )
                }
            }
    }

    override fun onEnd() {
        for (player in Bukkit.getOnlinePlayers()) {
            PlayerManager.fromBukkitPlayer(player).let {
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
                    Bukkit.getPlayer(civPlayer.uuid)?.let { bukkitPlayer -> NameTag.remove(bukkitPlayer) }
                }
            }
            civBeingRaided.raid = null
            civRaiding.raid = null
            CooldownTask.addCooldownTimer(civBeingRaided, CooldownTask.CooldownType.END_WAR)
            CooldownTask.addCooldownTimer(civRaiding, CooldownTask.CooldownType.END_WAR)
            CooldownTask.addCooldownTimer(civRaiding, CooldownTask.CooldownType.RAID)
        }
        Common.callEvent(RaidEndEvent(this))
    }


    init {
        launch()
    }
}