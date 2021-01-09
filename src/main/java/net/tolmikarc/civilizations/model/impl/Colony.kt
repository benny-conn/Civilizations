/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model.impl

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.model.Civ
import org.bukkit.Location
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*


data class Colony(val civilization: Civ, var id: Int, val warp: Location) : ConfigSerializable {
    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civilization", civilization.uuid)
        map.put("Id", id)
        map.put("Location", warp)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Colony {
            return Colony(
                CivManager.getByUUID(map["Civilization", UUID::class.java]),
                map.getInteger("Id"),
                map.getLocation("Location")
            )
        }
    }
}