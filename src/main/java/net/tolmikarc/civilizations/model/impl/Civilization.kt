/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model.impl

import net.tolmikarc.civilizations.chat.CivChannel
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.permissions.Permissions
import net.tolmikarc.civilizations.permissions.Toggleables
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.war.Damages
import net.tolmikarc.civilizations.war.Raid
import org.bukkit.Location
import org.mineacademy.fo.collection.SerializedMap
import java.sql.SQLException
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.collections.set

data class Civilization(override val uuid: UUID) : Civ {


    override var name: String? = null
        set(value) {
            if (value != null) {
                CivManager.byName[value.toLowerCase()] = this
            }
            field = value
        }
    override var description: String? = null
    override var power = 0
    override var leader: CPlayer? = null
    override var bank: Bank = Bank(this)
    override var home: Location? = null
    override var claims = Claims(this)
    override var warps: MutableMap<String, Location> = LinkedHashMap()

    override val citizens = mutableSetOf<CPlayer>()
    override var relationships = Relationships(this)

    override var damages: Damages? = null

    override var permissions: Permissions = Permissions(this)
    override var toggleables = Toggleables()

    override var raid: Raid? = null

    override val channel = CivChannel()

    override fun addPower(power: Int) {
        this.power += power
        CivManager.queueForSaving(this)
    }

    override fun removePower(power: Int) {
        if (this.power - power >= 0)
            this.power -= power
        else
            this.power = 0
        CivManager.queueForSaving(this)
    }


    override fun addWarp(name: String, location: Location) {
        warps[name] = location
        CivManager.saveAsync(this)
    }

    override fun removeWarp(warp: String) {
        warps.remove(warp)
        CivManager.saveAsync(this)
    }


    override fun addCitizen(player: CPlayer) {
        citizens.add(player)
        permissions.setPlayerGroup(player, permissions.defaultRank)
        addPower(Settings.POWER_CITIZENS_WEIGHT)
        if (Settings.ADD_PLAYER_POWER_TO_CIV) {
            addPower(player.power)
        }
        player.addPower(CivUtil.calculateFormulaForCiv(Settings.POWER_CITIZEN_FORMULA, this).toInt())
        CivManager.saveAsync(this)
    }

    override fun removeCitizen(player: CPlayer) {
        citizens.remove(player)
        permissions.playerGroupMap.remove(player.uuid)
        removePower(Settings.POWER_CITIZENS_WEIGHT)
        if (Settings.ADD_PLAYER_POWER_TO_CIV) {
            removePower(player.power)
        }
        player.removePower(CivUtil.calculateFormulaForCiv(Settings.POWER_CITIZEN_FORMULA, this).toInt())
        CivManager.saveAsync(this)
    }


    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.putIfExist("UUID", uuid)
        map.putIfExist("Name", name)
        map.putIfExist("Description", description)
        map.putIfExist("Power", power)
        map.putIfExist("Leader", leader?.uuid)
        map.putIfExist("Home", home)
        map.putIfExist("Claims", claims)
        map.putIfExist("Warps", warps)
        map.putIfExist(
            "Citizens",
            citizens.stream().map { it.uuid }.collect(Collectors.toSet())
        )
        map.putIfExist("Relationships", relationships)
        map.putIfExist("Bank", bank)
        map.putIfExist("Groups", permissions)
        map.putIfExist("Toggleables", toggleables)
        map.putIfExist("Region_Damages", damages)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Civilization {
            val uuid = map.get("UUID", UUID::class.java)
            val cache = Civilization(uuid)
            val name = map.getString("Name")
            val description = map.getString("Description")
            val power = map.getInteger("Power")
            var leader: CPlayer? = null
            try {
                leader =
                    if (PlayerDatastore.isStored(map.get("Leader", UUID::class.java)))
                        PlayerManager.getByUUID(map.get("Leader", UUID::class.java))
                    else null
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            val home = map.getLocation("Home")
            val claims = map.get("Claims", Claims::class.java)
            val warps: Map<String, Location>? = map.getMap("Warps", String::class.java, Location::class.java)
            val citizens: MutableSet<CPlayer> =
                map.getSet("Citizens", UUID::class.java).stream().filter(playerRemoveFilter)
                    .map { PlayerManager.getByUUID(it) }
                    .collect(Collectors.toSet())
            val relationships = map.get("Relationships", Relationships::class.java)
            val bank = map.get("Bank", Bank::class.java)
            val groups = map.get("Groups", Permissions::class.java)
            val toggleables = map.get("Toggleables", Toggleables::class.java)
            val regionDamages = map.get("Region_Damages", Damages::class.java)


            cache.name = name
            if (description != null) cache.description = description
            if (power != null) cache.power = power
            if (leader == null) leader = citizens.iterator().next()
            cache.leader = leader
            if (home != null) cache.home = home
            if (claims != null) cache.claims = claims
            if (warps != null) cache.warps = warps as MutableMap<String, Location>
            cache.citizens.addAll(citizens)
            if (relationships != null) cache.relationships = relationships
            if (bank != null) cache.bank = bank
            if (groups != null) cache.permissions = groups
            if (toggleables != null) cache.toggleables = toggleables
            if (regionDamages != null) cache.damages = regionDamages
            return cache
        }

        private val playerRemoveFilter = Predicate { uuid: UUID ->
            try {
                return@Predicate PlayerDatastore.isStored(uuid)
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            false
        }
    }
}