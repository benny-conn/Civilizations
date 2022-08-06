/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.war

import org.bukkit.Location
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable

class Damages : ConfigSerializable {
    // String = block data
    val brokenBlocksMap: MutableMap<Location, String> = LinkedHashMap()
    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Blocks_Map", brokenBlocksMap)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Damages {
            val damages = Damages()
            damages.brokenBlocksMap.putAll(map.getMap("Blocks_Map", Location::class.java, String::class.java))
            return damages
        }
    }
}