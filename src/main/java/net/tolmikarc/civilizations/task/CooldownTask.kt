/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.task

import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.AbstractMap.SimpleEntry
import java.util.concurrent.ConcurrentHashMap

class CooldownTask : BukkitRunnable() {
    override fun run() {
        val currentTime = System.currentTimeMillis()
        if (cooldowns.isNotEmpty()) {
            for (entry in cooldowns.keys) {
                val time = cooldowns[entry]!!
                if (time < currentTime) {
                    cooldowns.remove(entry)
                }
            }
        }
    }

    enum class CooldownType(val seconds: Int) {
        PVP(30), TELEPORT(10), RAID(Settings.RAID_LENGTH + Settings.RAID_COOLDOWN), TNT(Settings.RAID_TNT_COOLDOWN);
    }

    companion object {
        private val cooldowns: MutableMap<SimpleEntry<UUID, CooldownType>, Long> = ConcurrentHashMap()


        fun addCooldownTimer(uuid: UUID, type: CooldownType) {
            val map = SimpleEntry(uuid, type)
            cooldowns[map] = System.currentTimeMillis() + (type.seconds * 1000).toLong()
        }


        fun hasCooldown(uuid: UUID, type: CooldownType): Boolean {
            val map = SimpleEntry(uuid, type)
            return cooldowns.containsKey(map)
        }


        fun getCooldownRemaining(uuid: UUID, type: CooldownType): Int {
            val map = SimpleEntry(uuid, type)
            return if (cooldowns.containsKey(map)) ((cooldowns[map]!! - System.currentTimeMillis()) / 1000L).toInt() else 0
        }
    }
}