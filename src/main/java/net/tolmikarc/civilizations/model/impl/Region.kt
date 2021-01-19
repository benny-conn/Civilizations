/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model.impl

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.mineacademy.fo.BlockUtil
import org.mineacademy.fo.Common
import org.mineacademy.fo.Valid
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*

class Region(val id: Int, val primary: Location, val secondary: Location) : ConfigSerializable {

    private val correctedPoints: Array<Location>
        get() = run {
            Valid.checkBoolean(
                primary.world!!.name == secondary.world!!.name,
                "Points must be in one world! Primary: $primary != secondary: $secondary",
            )
            val x1 = primary.x
            val x2 = secondary.x
            val y1 = 0.0
            val y2 = 256.0
            val z1 = primary.z
            val z2 = secondary.z
            val primary = primary.clone()
            val secondary = secondary.clone()
            primary.x = x1.coerceAtMost(x2)
            primary.y = y1.coerceAtMost(y2)
            primary.z = z1.coerceAtMost(z2)
            secondary.x = x1.coerceAtLeast(x2)
            secondary.y = y1.coerceAtLeast(y2)
            secondary.z = z1.coerceAtLeast(z2)
            arrayOf(primary, secondary)
        }
    val center: Location
        get() {
            val centered = correctedPoints
            val primary = centered[0]
            val secondary = centered[1]
            return Location(
                primary.world,
                (primary.x + secondary.x) / 2.0,
                (primary.y + secondary.y) / 2.0,
                (primary.z + secondary.z) / 2.0
            )
        }
    val blocks: List<Block>
        get() = BlockUtil.getBlocks(correctedPoints[0], correctedPoints[1])

    val boundingBox: Set<Location>
        get() = BlockUtil.getBoundingBox(correctedPoints[0], correctedPoints[1])

    val entities: List<Entity?>
        get() {
            val found: MutableList<Entity> = LinkedList()
            val centered = correctedPoints
            val primary = centered[0]
            val secondary = centered[1]
            val xMin = primary.x.toInt() shr 4
            val xMax = secondary.x.toInt() shr 4
            val zMin = primary.z.toInt() shr 4
            val zMax = secondary.z.toInt() shr 4
            for (cx in xMin..xMax) {
                for (cz in zMin..zMax) {
                    val var11 = world!!.getChunkAt(cx, cz).entities
                    val var12 = var11.size
                    for (var13 in 0 until var12) {
                        val entity = var11[var13]
                        if (entity.isValid && isWithin(entity.location)) {
                            found.add(entity)
                        }
                    }
                }
            }
            return found
        }
    val world: World?
        get() = run {
            Valid.checkBoolean(
                primary.world!!.name == secondary.world!!
                    .name,
                "Worlds of this region not the same: " + primary.world + " != " + secondary.world
            )
            Bukkit.getWorld(primary.world!!.name)
        }

    fun isWithin(location: Location): Boolean {
        return if (location.world!!.name != primary.world!!.name) {
            false
        } else {
            val primary = correctedPoints[0]
            val secondary = correctedPoints[1]
            val x = location.x.toInt()
            val z = location.z.toInt()
            x in primary.blockX..secondary.blockX && z in primary.blockZ..secondary.blockZ
        }
    }

    override fun toString(): String {
        return this.javaClass.simpleName + "{id=" + id + ",location=" + Common.shortLocation(primary) + " - " + Common.shortLocation(
            secondary
        ) + "}"
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("ID", id)
        map.put("Primary", primary)
        map.put("Secondary", secondary)
        return map
    }

    companion object {

        @JvmStatic
        fun deserialize(map: SerializedMap): Region {
            val id = map.getInteger("ID")
            val prim = map.getLocation("Primary")
            val sec = map.getLocation("Secondary")
            return Region(id, prim, sec)
        }
    }
}