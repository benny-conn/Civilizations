/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.war

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.RaidParticleTask
import io.bennyc.civilizations.util.ClaimUtil
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.mineacademy.fo.Common
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.model.Countdown
import org.mineacademy.fo.remain.Remain

class Raid(val civBeingRaided: Civilization, val civRaiding: Civilization) : Countdown(io.bennyc.civilizations.settings.Settings.RAID_LENGTH) {

    // map of players and their lives
    val playersInvolved: MutableMap<io.bennyc.civilizations.model.CivPlayer, Int> = HashMap()
    private val openTasks = mutableSetOf<RaidParticleTask>()

    fun addPlayerToRaid(player: Player) {

        Remain.sendBossbarTimed(
            player,
            "${ChatColor.RED}${ChatColor.BOLD}Raid of ${ChatColor.DARK_RED}${ChatColor.BOLD}${civBeingRaided.name}",
            timeLeft
        )

        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let {

            playersInvolved.putIfAbsent(it, io.bennyc.civilizations.settings.Settings.RAID_LIVES)

            if (playersInvolved[it] == 0) return

            if (playersInvolved[it] == io.bennyc.civilizations.settings.Settings.RAID_LIVES) Common.callEvent(
                io.bennyc.civilizations.event.PlayerJoinRaidEvent(
                    this,
                    player
                )
            )

            if (Common.doesPluginExist("ProtocolLib")) {
                val onlinePlayersFromOppositeCivilization = mutableListOf<Player>()
                for (p in Bukkit.getOnlinePlayers()) {
                    if ((civRaiding == it.civilization && io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(p).civilization == civBeingRaided) || (civBeingRaided == it.civilization && io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(
                            p
                        ).civilization == civRaiding))
                        onlinePlayersFromOppositeCivilization.add(p)
                }
                val task = RaidParticleTask(player, onlinePlayersFromOppositeCivilization)
                Common.runTimerAsync(10, task)
                openTasks.add(task)

            }
        }
    }


    override fun onStart() {
        for (player in Bukkit.getOnlinePlayers()) {
            io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let {
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
        Common.callEvent(io.bennyc.civilizations.event.RaidBeginEvent(this))
    }

    override fun onTick() {
        if (timeLeft % 120 == 0)
            for (player in Bukkit.getOnlinePlayers()) {
                io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let {
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
            io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let {
                if (civBeingRaided.citizens.contains(it)) Messenger.info(
                    player,
                    "${io.bennyc.civilizations.settings.Settings.PRIMARY_COLOR}Raid over!"
                )
                if (civRaiding.citizens.contains(it)) Messenger.info(
                    player,
                    "${io.bennyc.civilizations.settings.Settings.PRIMARY_COLOR}Raid over!"
                )
            }
            openTasks.forEach { it.cancel() }
            civBeingRaided.raid = null
            civRaiding.raid = null
            CooldownTask.addCooldownTimer(civBeingRaided, CooldownTask.CooldownType.END_WAR)
            CooldownTask.addCooldownTimer(civRaiding, CooldownTask.CooldownType.END_WAR)
        }
        Common.callEvent(io.bennyc.civilizations.event.RaidEndEvent(this))
    }


    init {
        launch()
    }
}