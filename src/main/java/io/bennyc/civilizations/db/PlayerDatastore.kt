/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.db

import io.bennyc.civilizations.AsyncEnvironment
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.model.CivPlayer
import org.mineacademy.fo.Common
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.debug.Debugger
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*


object PlayerDatastore : io.bennyc.civilizations.db.Datastore() {


    override fun createTablesIfNotExist() {
        update("CREATE TABLE IF NOT EXISTS {table}(uuid varchar(64) PRIMARY KEY, Name varchar(64), Civilization varchar(64), Power bigint, RaidBlocks bigint,Updated bigint)")
        removeOldEntries()
    }

    fun load(cache: io.bennyc.civilizations.model.CivPlayer) {
        try {
            if (!isStored(cache.uuid)) {
                return
            }
            val results: ResultSet? = query("SELECT * FROM {table} WHERE uuid='" + cache.uuid + "'")
            if (results != null) {
                if (results.next()) {
                    Debugger.debug("sql", "Loading data for player: ${results.getString("Name")}")
                    val name = results.getString("Name")
                    val civUUIDAsString = results.getString("Civilization")
                    val civUUID = if (civUUIDAsString != null) UUID.fromString(civUUIDAsString) else null
                    val civilization =
                        if (civUUID?.let { io.bennyc.civilizations.db.CivDatastore.isStored(it) } == true) io.bennyc.civilizations.manager.CivManager.getByUUID(civUUID) else null
                    val raidBlocks = results.getInt("RaidBlocks")
                    if (name != null) cache.playerName = name
                    if (civilization != null) cache.civilization = civilization
                    cache.raidBlocksDestroyed = raidBlocks
                }
                results.close()
            }

        } catch (e: SQLException) {
            e.printStackTrace()
            Common.error(e, "Could not load data for CivPlayer: " + cache.uuid)
        }
    }

    fun save(cache: io.bennyc.civilizations.model.CivPlayer) {
        try {
            val map = SerializedMap().apply {
                put("Name", cache.playerName)
                cache.civilization?.let { put("Civilization", it.uuid) }
                put("Power", cache.power)
                put("RaidBlocks", cache.raidBlocksDestroyed)
                put("Updated", System.currentTimeMillis())
                Debugger.debug("sql", "Creating Map: ${toJson()}")
            }
            if (isStored(cache.uuid)) {
                update(map, cache.uuid)
                Debugger.debug("sql", "Updating ${map.toJson()}")
            } else {
                map.put("uuid", cache.uuid)
                insert(map)
                Debugger.debug("sql", "Inserting ${map.toJson()}")
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            Common.error(e, "Could not save Player: " + cache.playerName)
        }
    }

    fun loadAll() {
        Common.log("Loading Data from Datastores")
        io.bennyc.civilizations.AsyncEnvironment.run {
            try {
                val results: ResultSet? = query("SELECT * FROM {table}")
                if (results != null) {
                    while (results.next()) {
                        val uuid = UUID.fromString(results.getString("UUID"))
                        io.bennyc.civilizations.manager.PlayerManager.getByUUID(uuid)
                    }
                    results.close()
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                Common.logFramed(true, "Could not load all caches. Disabling plugin")
            }
        }
    }
}