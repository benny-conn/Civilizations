/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.db

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.model.Bank
import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.war.Damages
import org.bukkit.Location
import org.mineacademy.fo.Common
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.debug.Debugger
import java.sql.ResultSet
import java.sql.SQLException

object CivDatastore : Datastore() {


    override fun createTablesIfNotExist() {
        update("CREATE TABLE IF NOT EXISTS {table}(uuid varchar(64) PRIMARY KEY, Name varchar(64), Data text, Updated bigint)")
        removeOldEntries()
    }

    fun load(civ: Civilization) {
        try {
            if (!isStored(civ.uuid)) {
                CivManager.removeCiv(civ)
                return
            }
            val results: ResultSet? = query("SELECT * FROM {table} WHERE uuid='" + civ.uuid + "'")
            if (results != null) {
                if (results.next()) {
                    val dataRaw = results.getString("Data")
                    val data = SerializedMap.fromJson(dataRaw)
                    if (data.isEmpty) return
                    val deserializedCiv = Civilization.deserialize(data)
                    if (deserializedCiv.citizens.isEmpty()) {
                        CivManager.removeCiv(civ)
                        return
                    }
                    val name: String? = deserializedCiv.name
                    Debugger.debug("sql", "Loading data for civ: $name")
                    val description = deserializedCiv.description
                    val power: Int = deserializedCiv.power
                    val leader = deserializedCiv.leader
                    val home: Location? = deserializedCiv.home
                    val claims = deserializedCiv.claims
                    val warps: MutableMap<String, Location> = deserializedCiv.warps
                    val citizens: MutableSet<CivPlayer> = deserializedCiv.citizens
                    val relationships = deserializedCiv.relationships
                    val bank: Bank = deserializedCiv.bank
                    val permissions = deserializedCiv.permissions
                    val toggleables = deserializedCiv.toggleables
                    val damages: Damages? = deserializedCiv.damages

                    civ.apply {
                        if (name != null) this.name = name
                        if (description != null) this.description = description
                        this.power = power
                        this.leader = leader
                        if (home != null) this.home = home
                        this.claims = claims
                        this.warps = warps
                        this.citizens.addAll(citizens)
                        this.relationships = relationships
                        this.bank = bank
                        this.permissions = permissions
                        this.toggleables = toggleables
                        if (damages != null) this.damages = damages
                    }
                } else CivManager.removeCiv(civ)
            }

        } catch (e: SQLException) {
            Common.error(e, "Could not load data for CivPlayer: " + civ.uuid)
        }
    }

    fun save(cache: Civilization) {
        try {
            val map: SerializedMap = SerializedMap().apply {
                put("Name", cache.name)
                put("Data", cache)
                put("Updated", System.currentTimeMillis())
            }
            Debugger.debug("sql", "Creating Map: ${map.toJson()}")
            if (isStored(cache.uuid)) {
                update(map, cache.uuid)
                Debugger.debug("sql", "Updating Map: ${map.toJson()}")
            } else {
                map.put("uuid", cache.uuid)
                insert(map)
                Debugger.debug("sql", "Inserting Map: ${map.toJson()}")
            }
        } catch (e: SQLException) {
            Common.error(e, "Error saving Civ: " + cache.name)
        }
    }

}