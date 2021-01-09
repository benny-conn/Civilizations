/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.permissions

import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable

data class PermissionGroup(var name: String, val permissions: MutableSet<PermissionType>) : ConfigSerializable {

    fun adjust(type: PermissionType, value: Boolean) {
        if (value)
            permissions.add(type)
        else
            permissions.remove(type)
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Name", name)
        map.put("Permissions", permissions.map { it.name })
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): PermissionGroup {
            return PermissionGroup(
                map.getString("Name"),
                map.getStringList("Permissions").map { PermissionType.valueOf(it) }.toMutableSet()
            )
        }
    }

}