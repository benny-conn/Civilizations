/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*

class Relationships(val civ: Civilization) : ConfigSerializable {
    val allies = mutableSetOf<Civilization>()
    val enemies = mutableSetOf<Civilization>()
    val outlaws = mutableSetOf<CivPlayer>()

    val warring: Set<Civilization>
        get() {
            val set = mutableSetOf<Civilization>()
            for (c in enemies) {
                if (c.relationships.enemies.contains(civ))
                    set.add(c)
            }
            return set
        }

    fun addAlly(ally: Civilization) {
        allies.add(ally)
        CivManager.saveAsync(civ)
    }

    fun removeAlly(ally: Civilization) {
        allies.remove(ally)
        CivManager.saveAsync(civ)
    }

    fun addEnemy(enemy: Civilization) {
        enemies.add(enemy)
        CivManager.saveAsync(civ)
    }

    fun removeEnemy(enemy: Civilization) {
        enemies.remove(enemy)
        CivManager.saveAsync(civ)
    }

    fun addOutlaw(player: CivPlayer) {
        outlaws.add(player)
        CivManager.saveAsync(civ)
    }

    fun removeOutlaw(player: CivPlayer) {
        outlaws.remove(player)
        CivManager.saveAsync(civ)
    }


    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civ", civ.uuid)
        map.put("Allies", allies.map { it.uuid })
        map.put("Enemies", enemies.map { it.uuid })
        map.put("Outlaws", outlaws.map { it.uuid })
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Relationships {
            val relationships = Relationships(CivManager.getByUUID(map.get("Civ", UUID::class.java)))
            relationships.allies.addAll(map.getSet("Allies", UUID::class.java).map { CivManager.getByUUID(it) })
            relationships.enemies.addAll(map.getSet("Enemies", UUID::class.java).map { CivManager.getByUUID(it) })
            relationships.outlaws.addAll(map.getSet("Outlaws", UUID::class.java).map { PlayerManager.getByUUID(it) })
            return relationships
        }
    }
}