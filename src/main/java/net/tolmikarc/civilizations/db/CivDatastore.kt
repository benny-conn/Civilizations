/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.db

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Bank
import net.tolmikarc.civilizations.model.impl.Civilization
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.model.impl.Plot
import net.tolmikarc.civilizations.war.RegionDamages
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
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

    fun load(civ: Civ) {
        try {
            if (!isStored(civ.uuid)) {
                CivManager.removeCiv(civ)
                return
            }
            val results: ResultSet? = query("SELECT * FROM {table} WHERE uuid='" + civ.uuid + "'")
            if (results != null) {
                if (results.next()) {
                    val dataRaw = results.getString("Data")
                    println(dataRaw)
                    val data = SerializedMap.fromJson(dataRaw)
                    if (data.isEmpty) return
                    val deserializedCiv = Civilization.deserialize(data)
                    if (deserializedCiv.citizens.isEmpty()) {
                        CivManager.removeCiv(civ)
                        return
                    }
                    val name: String? = deserializedCiv.name
                    Debugger.debug("sql", "Loading data for civ: $name")
                    val power: Int = deserializedCiv.power
                    val leader = deserializedCiv.leader
                    val home: Location? = deserializedCiv.home
                    val claims: MutableSet<Claim> = deserializedCiv.claims

                    val plots: MutableSet<Plot> = deserializedCiv.plots
                    val warps: MutableMap<String, Location> = deserializedCiv.warps
                    val idNumber: Int = deserializedCiv.idNumber
                    val totalBlocksCount: Int = deserializedCiv.totalBlocksCount
                    val citizens: MutableSet<CPlayer> = deserializedCiv.citizens
                    val allies: MutableSet<Civ> = deserializedCiv.allies
                    val enemies: MutableSet<Civ> = deserializedCiv.enemies
                    val outlaws: MutableSet<CPlayer> = deserializedCiv.outlaws
                    val bank: Bank = deserializedCiv.bank
                    val banner: ItemStack? = deserializedCiv.banner
                    val book: ItemStack? = deserializedCiv.book
                    val permissions = deserializedCiv.permissionGroups
                    val toggleables = deserializedCiv.claimToggleables
                    val regionDamages: RegionDamages? = deserializedCiv.regionDamages

                    civ.apply {
                        if (name != null) this.name = name
                        this.power = power
                        this.leader = leader
                        if (home != null) this.home = home
                        println("$home AGAIN")
                        this.claims.addAll(claims)
                        println("$claims again")
                        this.plots.addAll(plots)
                        this.warps = warps
                        this.idNumber = idNumber
                        this.totalBlocksCount = totalBlocksCount
                        this.citizens.addAll(citizens)
                        this.allies.addAll(allies)
                        this.enemies.addAll(enemies)
                        this.outlaws.addAll(outlaws)
                        this.bank = bank
                        if (banner != null) this.banner = banner
                        if (book != null) this.book = book
                        this.permissionGroups = permissions
                        this.claimToggleables = toggleables
                        if (regionDamages != null) this.regionDamages = regionDamages
                    }
                } else CivManager.removeCiv(civ)
            }

        } catch (e: SQLException) {
            Common.error(e, "Could not load data for CivPlayer: " + civ.uuid)
        }
    }

    fun save(cache: Civ) {
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