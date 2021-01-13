/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.db

import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import org.mineacademy.fo.Common
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.debug.Debugger
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*


object PlayerDatastore : Datastore() {


    override fun createTablesIfNotExist() {
        update("CREATE TABLE IF NOT EXISTS {table}(uuid varchar(64) PRIMARY KEY, Name varchar(64), Civilization varchar(64), Power bigint, RaidBlocks bigint,Updated bigint)")
        removeOldEntries()
    }

    fun load(cache: CPlayer) {
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
                        if (civUUID?.let { CivDatastore.isStored(it) } == true) CivManager.getByUUID(civUUID) else null
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

    fun save(cache: CPlayer) {
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
        AsyncEnvironment.run {
            try {
                val results: ResultSet? = query("SELECT * FROM {table}")
                if (results != null) {
                    while (results.next()) {
                        val uuid = UUID.fromString(results.getString("UUID"))
                        PlayerManager.getByUUID(uuid)
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