/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.permissions

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap

data class Ranks(val civ: Civ) : ConfigSerializable {
    val ranks: MutableSet<Rank> = HashSet()
    val adminGroups: MutableSet<Rank> = HashSet()
    var playerGroupMap: MutableMap<UUID, Rank> = LinkedHashMap()
    var defaultRank: Rank = Rank(Settings.DEFAULT_GROUP.name, Settings.DEFAULT_GROUP.permissions)
        set(value) {
            field = value
            ranks.add(value)
        }
    var outsiderRank: Rank =
        Rank(Settings.OUTSIDER_GROUP.name, Settings.OUTSIDER_GROUP.permissions)
        set(value) {
            field = value
            ranks.add(value)
        }
    var allyRank: Rank = Rank(Settings.ALLY_GROUP.name, Settings.ALLY_GROUP.permissions)
        set(value) {
            field = value
            ranks.add(value)
        }
    var enemyRank: Rank = Rank(Settings.ENEMY_GROUP.name, Settings.ENEMY_GROUP.permissions)
        set(value) {
            field = value
            ranks.add(value)
        }


    fun getGroupByName(name: String): Rank? {
        return ranks.find { it.name == name }
    }

    fun setPlayerGroup(player: CPlayer, group: Rank) {
        playerGroupMap[player.uuid] = group
    }

    fun getPlayerGroup(player: CPlayer): Rank {
        if (civ.relationships.allies.any { it.citizens.contains(player) })
            return allyRank
        if (civ.relationships.enemies.any { it.citizens.contains(player) })
            return enemyRank
        return playerGroupMap.getOrDefault(player.uuid, outsiderRank)
    }


    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civ", civ.uuid)
        map.put("Groups", ranks)
        map.put("Admin_Groups", adminGroups)
        map.put("Player_Groups", playerGroupMap)
        map.put("Default", defaultRank)
        map.put("Outsider", outsiderRank)
        map.put("Ally", allyRank)
        map.put("Enemy", enemyRank)
        return map

    }


    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Ranks {
            val groups = Ranks(CivManager.getByUUID(map.get("Civ", UUID::class.java)))
            groups.ranks.addAll(map.getSet("Groups", Rank::class.java))
            groups.adminGroups.addAll(map.getSet("Admin_Groups", Rank::class.java))
            groups.playerGroupMap = map.getMap("Player_Groups", UUID::class.java, Rank::class.java)
            groups.defaultRank = map.get("Default", Rank::class.java)
            groups.outsiderRank = map.get("Outsider", Rank::class.java)
            groups.allyRank = map.get("Ally", Rank::class.java)
            groups.enemyRank = map.get("Enemy", Rank::class.java)
            return groups
        }
    }

}