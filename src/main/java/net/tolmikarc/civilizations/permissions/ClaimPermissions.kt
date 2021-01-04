/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.permissions

import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable

class ClaimPermissions : ConfigSerializable {
    // first dimension is group second dimension is type
    var permissions = Array(PermGroup.values().size) { BooleanArray(PermType.values().size) }

    constructor() {
        permissions[PermGroup.OUTSIDER.id][PermType.BUILD.id] = Settings.DEFAULT_OUTSIDER_BUILD!!
        permissions[PermGroup.OUTSIDER.id][PermType.BREAK.id] = Settings.DEFAULT_OUTSIDER_BREAK!!
        permissions[PermGroup.OUTSIDER.id][PermType.SWITCH.id] = Settings.DEFAULT_OUTSIDER_SWITCH!!
        permissions[PermGroup.OUTSIDER.id][PermType.INTERACT.id] = Settings.DEFAULT_OUTSIDER_INTERACT!!
        permissions[PermGroup.ALLY.id][PermType.BUILD.id] = Settings.DEFAULT_ALLY_BUILD!!
        permissions[PermGroup.ALLY.id][PermType.BREAK.id] = Settings.DEFAULT_ALLY_BREAK!!
        permissions[PermGroup.ALLY.id][PermType.SWITCH.id] = Settings.DEFAULT_ALLY_SWITCH!!
        permissions[PermGroup.ALLY.id][PermType.INTERACT.id] = Settings.DEFAULT_ALLY_INTERACT!!
        permissions[PermGroup.MEMBER.id][PermType.BUILD.id] = Settings.DEFAULT_MEMBER_BUILD!!
        permissions[PermGroup.MEMBER.id][PermType.BREAK.id] = Settings.DEFAULT_MEMBER_BREAK!!
        permissions[PermGroup.MEMBER.id][PermType.SWITCH.id] = Settings.DEFAULT_MEMBER_SWITCH!!
        permissions[PermGroup.MEMBER.id][PermType.INTERACT.id] = Settings.DEFAULT_MEMBER_INTERACT!!
        permissions[PermGroup.OFFICIAL.id][PermType.BUILD.id] = Settings.DEFAULT_OFFICIAL_BUILD!!
        permissions[PermGroup.OFFICIAL.id][PermType.BREAK.id] = Settings.DEFAULT_OFFICIAL_BREAK!!
        permissions[PermGroup.OFFICIAL.id][PermType.SWITCH.id] = Settings.DEFAULT_OFFICIAL_SWITCH!!
        permissions[PermGroup.OFFICIAL.id][PermType.INTERACT.id] = Settings.DEFAULT_OFFICIAL_INTERACT!!
    }

    constructor(permissions: Array<BooleanArray>) {
        this.permissions = permissions
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Outsider_Build", permissions[PermGroup.OUTSIDER.id][PermType.BUILD.id])
        map.put("Outsider_Break", permissions[PermGroup.OUTSIDER.id][PermType.BREAK.id])
        map.put("Outsider_Switch", permissions[PermGroup.OUTSIDER.id][PermType.SWITCH.id])
        map.put("Outsider_Interact", permissions[PermGroup.OUTSIDER.id][PermType.INTERACT.id])
        map.put("Official_Build", permissions[PermGroup.OFFICIAL.id][PermType.BUILD.id])
        map.put("Official_Break", permissions[PermGroup.OFFICIAL.id][PermType.BREAK.id])
        map.put("Official_Switch", permissions[PermGroup.OFFICIAL.id][PermType.SWITCH.id])
        map.put("Official_Interact", permissions[PermGroup.OFFICIAL.id][PermType.INTERACT.id])
        map.put("Member_Build", permissions[PermGroup.MEMBER.id][PermType.BUILD.id])
        map.put("Member_Break", permissions[PermGroup.MEMBER.id][PermType.BREAK.id])
        map.put("Member_Switch", permissions[PermGroup.MEMBER.id][PermType.SWITCH.id])
        map.put("Member_Interact", permissions[PermGroup.MEMBER.id][PermType.INTERACT.id])
        map.put("Ally_Build", permissions[PermGroup.ALLY.id][PermType.BUILD.id])
        map.put("Ally_Break", permissions[PermGroup.ALLY.id][PermType.BREAK.id])
        map.put("Ally_Switch", permissions[PermGroup.ALLY.id][PermType.SWITCH.id])
        map.put("Ally_Interact", permissions[PermGroup.ALLY.id][PermType.INTERACT.id])
        return map
    }

    fun adjustPerm(permissionType: String, permissionGroup: String, permissionValue: String): Boolean {
        return if (permissionValue.equals("true", ignoreCase = true) ||
            permissionValue.equals("false", ignoreCase = true)
        ) {
            permissions[getPermGroup(permissionGroup)][getPermType(permissionType)] = permissionValue.toBoolean()
            true
        } else false
    }

    @Throws(IllegalArgumentException::class)
    private fun getPermGroup(permGroup: String): Int {
        return PermGroup.valueOf(permGroup.toUpperCase()).id
    }

    @Throws(IllegalArgumentException::class)
    private fun getPermType(permType: String): Int {
        return PermType.valueOf(permType.toUpperCase()).id
    }

    fun getPermGroups(permType: PermType): List<String> {
        val list: MutableList<String> = ArrayList()
        for (permGroup in PermGroup.values()) {
            if (permissions[permGroup.id][permType.id])
                list.add(permGroup.name.capitalize())
        }
        return list.toList()
    }

    enum class PermGroup(val id: Int) {
        OUTSIDER(0), MEMBER(1), ALLY(2), OFFICIAL(3);
    }

    enum class PermType(val id: Int) {
        BUILD(0), BREAK(1), SWITCH(2), INTERACT(3);
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): ClaimPermissions {
            val permissions = Array(PermGroup.values().size) { BooleanArray(PermType.values().size) }
            permissions[PermGroup.OUTSIDER.id][PermType.BUILD.id] = map.getBoolean("Outsider_Build")
            permissions[PermGroup.OUTSIDER.id][PermType.BREAK.id] = map.getBoolean("Outsider_Break")
            permissions[PermGroup.OUTSIDER.id][PermType.SWITCH.id] = map.getBoolean("Outsider_Switch")
            permissions[PermGroup.OUTSIDER.id][PermType.INTERACT.id] = map.getBoolean("Outsider_Interact")
            permissions[PermGroup.MEMBER.id][PermType.BUILD.id] = map.getBoolean("Member_Build")
            permissions[PermGroup.MEMBER.id][PermType.BREAK.id] = map.getBoolean("Member_Break")
            permissions[PermGroup.MEMBER.id][PermType.SWITCH.id] = map.getBoolean("Member_Switch")
            permissions[PermGroup.MEMBER.id][PermType.INTERACT.id] = map.getBoolean("Member_Interact")
            permissions[PermGroup.ALLY.id][PermType.BUILD.id] = map.getBoolean("Ally_Build")
            permissions[PermGroup.ALLY.id][PermType.BREAK.id] = map.getBoolean("Ally_Break")
            permissions[PermGroup.ALLY.id][PermType.SWITCH.id] = map.getBoolean("Ally_Switch")
            permissions[PermGroup.ALLY.id][PermType.INTERACT.id] = map.getBoolean("Ally_Interact")
            permissions[PermGroup.OFFICIAL.id][PermType.BUILD.id] = map.getBoolean("Official_Build")
            permissions[PermGroup.OFFICIAL.id][PermType.BREAK.id] = map.getBoolean("Official_Break")
            permissions[PermGroup.OFFICIAL.id][PermType.SWITCH.id] = map.getBoolean("Official_Switch")
            permissions[PermGroup.OFFICIAL.id][PermType.INTERACT.id] = map.getBoolean("Official_Interact")
            return ClaimPermissions(permissions)
        }
    }
}