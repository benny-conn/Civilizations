/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.permissions

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.model.CivPlayer
import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*

data class Permissions(val civ: Civilization) : ConfigSerializable {
    val allRankNames: List<String>
        get() {
            val list = mutableListOf<String>()
            list.addAll(ranks.map { it.name })
            list.add(defaultRank.name)
            list.add(outsiderRank.name)
            list.add(enemyRank.name)
            list.add(allyRank.name)
            return list.toList()
        }
    var defaultRank: Rank = Rank(
        Settings.DEFAULT_GROUP.name,
        Settings.DEFAULT_GROUP.permissions
    )
    var outsiderRank: Rank = Rank(
        Settings.OUTSIDER_GROUP.name,
        Settings.OUTSIDER_GROUP.permissions
    )
    var allyRank: Rank = Rank(
        Settings.ALLY_GROUP.name,
        Settings.ALLY_GROUP.permissions
    )
    var enemyRank: Rank = Rank(
        Settings.ENEMY_GROUP.name,
        Settings.ENEMY_GROUP.permissions
    )
    val ranks: MutableSet<Rank> = mutableSetOf(defaultRank, outsiderRank, allyRank, enemyRank)


    var playerGroupMap: MutableMap<UUID, Rank> = LinkedHashMap()


    fun getGroupByName(name: String): Rank? {
        return when {
            name.equals(defaultRank.name, true) -> defaultRank
            name.equals(outsiderRank.name, true) -> outsiderRank
            name.equals(allyRank.name, true) -> allyRank
            name.equals(enemyRank.name, true) -> enemyRank
            else -> ranks.find { it.name.equals(name, true) }
        }
    }

    fun setPlayerGroup(player: CivPlayer, group: Rank) {
        playerGroupMap[player.uuid] = group
    }

    fun getPlayerGroup(player: CivPlayer): Rank {
        if (civ.leader == player) return defaultRank
        if (civ.relationships.allies.any { it.citizens.contains(player) })
            return allyRank
        if (civ.relationships.enemies.any { it.citizens.contains(player) })
            return enemyRank
        return playerGroupMap.getOrDefault(player.uuid, outsiderRank)
    }


    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civ", civ.uuid)

        map.put("Player_Groups", playerGroupMap)
        map.put("Default", defaultRank)
        map.put("Outsider", outsiderRank)
        map.put("Ally", allyRank)
        map.put("Enemy", enemyRank)
        return map

    }


    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Permissions {
            val groups =
                Permissions(CivManager.getByUUID(map.get("Civ", UUID::class.java)))
            groups.playerGroupMap = map.getMap("Player_Groups", UUID::class.java, Rank::class.java)
            groups.defaultRank = map.get("Default", Rank::class.java)
            groups.outsiderRank = map.get("Outsider", Rank::class.java)
            groups.allyRank = map.get("Ally", Rank::class.java)
            groups.enemyRank = map.get("Enemy", Rank::class.java)
            groups.ranks.addAll(listOf(groups.defaultRank, groups.outsiderRank, groups.allyRank, groups.enemyRank))
            return groups
        }
    }

}