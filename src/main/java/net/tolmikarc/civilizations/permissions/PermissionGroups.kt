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

data class PermissionGroups(val civ: Civ) : ConfigSerializable {
    val groups: MutableSet<PermissionGroup> = HashSet()
    val adminGroups: MutableSet<PermissionGroup> = HashSet()
    var playerGroupMap: MutableMap<UUID, PermissionGroup> = LinkedHashMap()
    var defaultGroup: PermissionGroup = PermissionGroup(Settings.DEFAULT_GROUP.name, Settings.DEFAULT_GROUP.permissions)
        set(value) {
            field = value
            groups.add(value)
        }
    var outsiderGroup: PermissionGroup =
        PermissionGroup(Settings.OUTSIDER_GROUP.name, Settings.OUTSIDER_GROUP.permissions)
        set(value) {
            field = value
            groups.add(value)
        }
    var allyGroup: PermissionGroup = PermissionGroup(Settings.ALLY_GROUP.name, Settings.ALLY_GROUP.permissions)
        set(value) {
            field = value
            groups.add(value)
        }
    var enemyGroup: PermissionGroup = PermissionGroup(Settings.ENEMY_GROUP.name, Settings.ENEMY_GROUP.permissions)
        set(value) {
            field = value
            groups.add(value)
        }


    fun getGroupByName(name: String): PermissionGroup? {
        return groups.find { it.name == name }
    }

    fun setPlayerGroup(player: CPlayer, group: PermissionGroup) {
        playerGroupMap[player.uuid] = group
    }

    fun getPlayerGroup(player: CPlayer): PermissionGroup {
        if (civ.allies.any { it.citizens.contains(player) })
            return allyGroup
        if (civ.enemies.any { it.citizens.contains(player) })
            return enemyGroup
        return playerGroupMap.getOrDefault(player.uuid, outsiderGroup)
    }


    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civ", civ.uuid)
        map.put("Groups", groups)
        map.put("Admin_Groups", adminGroups)
        map.put("Player_Groups", playerGroupMap)
        map.put("Default", defaultGroup)
        map.put("Outsider", outsiderGroup)
        map.put("Ally", allyGroup)
        map.put("Enemy", enemyGroup)
        return map

    }


    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): PermissionGroups {
            val groups = PermissionGroups(CivManager.getByUUID(map.get("Civ", UUID::class.java)))
            groups.groups.addAll(map.getSet("Groups", PermissionGroup::class.java))
            groups.adminGroups.addAll(map.getSet("Admin_Groups", PermissionGroup::class.java))
            groups.playerGroupMap = map.getMap("Player_Groups", UUID::class.java, PermissionGroup::class.java)
            groups.defaultGroup = map.get("Default", PermissionGroup::class.java)
            groups.outsiderGroup = map.get("Outsider", PermissionGroup::class.java)
            groups.allyGroup = map.get("Ally", PermissionGroup::class.java)
            groups.enemyGroup = map.get("Enemy", PermissionGroup::class.java)
            return groups
        }
    }

}