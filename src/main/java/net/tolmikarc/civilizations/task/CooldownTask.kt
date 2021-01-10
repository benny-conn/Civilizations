/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.task

import net.tolmikarc.civilizations.model.UniquelyIdentifiable
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
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


    companion object {

        fun Int.toMilis(): Long {
            return (this * 1000).toLong()
        }

        private val cooldowns: MutableMap<AbstractMap.SimpleEntry<UniquelyIdentifiable, CooldownType>, Long> =
            ConcurrentHashMap()


        fun addCooldownTimer(entity: UniquelyIdentifiable, type: CooldownType) {
            val cooldown = AbstractMap.SimpleEntry(entity, type)
            cooldowns[cooldown] = System.currentTimeMillis() + type.seconds.toMilis()
        }


        fun hasCooldown(entity: UniquelyIdentifiable, type: CooldownType): Boolean {
            val cooldown = AbstractMap.SimpleEntry(entity, type)
            return cooldowns.containsKey(cooldown)
        }


        fun getCooldownRemaining(entity: UniquelyIdentifiable, type: CooldownType): Int {
            val cooldown = AbstractMap.SimpleEntry(entity, type)
            return if (cooldowns.containsKey(cooldown)) ((cooldowns[cooldown]!! - System.currentTimeMillis()) / 1000L).toInt() else 0
        }
    }

    enum class CooldownType(val seconds: Int) {
        PVP(Settings.PVP_TOGGLE_COOLDOWN), TELEPORT(Settings.TELEPORT_COOLDOWN), RAID(Settings.RAID_COOLDOWN), TNT(
            Settings.RAID_TNT_COOLDOWN
        ),
        END_WAR(
            Settings.RAID_COOLDOWN
        ),
        RESPAWN_PROTECTION(Settings.RESPAWN_PROTECTION)
    }


}