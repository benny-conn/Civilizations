/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model.impl

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*

class Relationships(val civ: Civ) : ConfigSerializable {
    val allies = mutableSetOf<Civ>()
    val enemies = mutableSetOf<Civ>()
    val outlaws = mutableSetOf<CPlayer>()

    val warring: Set<Civ>
        get() {
            val set = mutableSetOf<Civ>()
            for (c in enemies) {
                if (c.relationships.enemies.contains(civ))
                    set.add(c)
            }
            return set
        }

    fun addAlly(ally: Civ) {
        allies.add(ally)
        CivManager.saveAsync(civ)
    }

    fun removeAlly(ally: Civ) {
        allies.remove(ally)
        CivManager.saveAsync(civ)
    }

    fun addEnemy(enemy: Civ) {
        enemies.add(enemy)
        CivManager.saveAsync(civ)
    }

    fun removeEnemy(enemy: Civ) {
        enemies.remove(enemy)
        CivManager.saveAsync(civ)
    }

    fun addOutlaw(player: CPlayer) {
        outlaws.add(player)
        CivManager.saveAsync(civ)
    }

    fun removeOutlaw(player: CPlayer) {
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