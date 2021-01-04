/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.db

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Settings
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

    fun load(cache: CivPlayer) {
        try {
            if (!isStored(cache.playerUUID)) {
                return
            }
            val results: ResultSet? = query("SELECT * FROM {table} WHERE uuid='" + cache.playerUUID + "'")
            if (results != null) {
                if (results.next()) {
                    Debugger.debug("sql", "Loading data for player: ${results.getString("Name")}")
                    val name = results.getString("Name")
                    val civUUIDAsString = results.getString("Civilization")
                    val civUUID = if (civUUIDAsString != null) UUID.fromString(civUUIDAsString) else null
                    val civilization =
                        if (civUUID?.let { CivDatastore.isStored(it) } == true) Civilization.fromUUID(civUUID) else null
                    val raidBlocks = results.getInt("RaidBlocks")
                    if (name != null) cache.playerName = name
                    if (civilization != null) cache.civilization = civilization
                    cache.raidBlocksDestroyed = raidBlocks
                }
                results.close()
            }

        } catch (e: SQLException) {
            e.printStackTrace()
            Common.error(e, "Could not load data for CivPlayer: " + cache.playerUUID)
        }
    }

    fun save(cache: CivPlayer) {
        try {
            val map = SerializedMap().apply {
                put("Name", cache.playerName)
                if (cache.civilization != null) put("Civilization", cache.civilization!!.uuid)
                put("Power", cache.power)
                put("RaidBlocks", cache.raidBlocksDestroyed)
                put("Updated", System.currentTimeMillis())
                Debugger.debug("sql", "Creating Map: ${toJson()}")
            }
            if (isStored(cache.playerUUID)) {
                update(map, cache.playerUUID)
                Debugger.debug("sql", "Updating ${map.toJson()}")
            } else {
                map.put("uuid", cache.playerUUID)
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
        try {
            val results: ResultSet? = query("SELECT * FROM {table}")
            if (results != null) {
                while (results.next()) {
                    val uuid = UUID.fromString(results.getString("UUID"))
                    CivPlayer.initialLoadFromDatabase(uuid)
                }
                results.close()
            }

            Common.log("Finished Loading Data from Datastores")
        } catch (e: SQLException) {
            e.printStackTrace()
            Common.logFramed(true, "Could not load all caches. Disabling plugin")
        }
    }

    override val expirationDays: Int
        get() = Settings.DELETE_AFTER

}