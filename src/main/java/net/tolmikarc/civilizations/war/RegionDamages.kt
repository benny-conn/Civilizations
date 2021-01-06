/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.war

import org.bukkit.Location
import org.bukkit.block.Block
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*
import kotlin.collections.ArrayList

class RegionDamages : ConfigSerializable {
    // String = block data
    val brokenBlocksMap: MutableMap<Location, String> = LinkedHashMap()
    val cleanUpSet: MutableList<Block> = ArrayList()
    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Blocks_Map", brokenBlocksMap)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): RegionDamages {
            val damages = RegionDamages()
            damages.brokenBlocksMap.putAll(map.getMap("Blocks_Map", Location::class.java, String::class.java))
            return damages
        }
    }
}