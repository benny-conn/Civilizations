/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.model

import io.bennyc.civilizations.chat.CivChannel
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.permissions.Permissions
import io.bennyc.civilizations.permissions.Toggleables
import io.bennyc.civilizations.settings.Settings
import io.bennyc.civilizations.util.CivUtil
import io.bennyc.civilizations.util.ClaimUtil
import io.bennyc.civilizations.util.WarUtil
import io.bennyc.civilizations.war.Damages
import io.bennyc.civilizations.war.Raid
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.sql.SQLException
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.collections.set

data class Civilization(override val uuid: UUID) : UniquelyIdentifiable, ConfigSerializable {


    var name: String? = null
        set(value) {
            if (value != null) {
                CivManager.byName[value.lowercase(Locale.getDefault())] = this
            }
            field = value
        }
    var description: String? = null
    var power = 0
    var leader: CivPlayer? = null
    var bank: Bank = Bank(this)
    var home: Location? = null
    var claims = Claims(this)
    var warps: MutableMap<String, Location> = LinkedHashMap()

    val citizens = mutableSetOf<CivPlayer>()
    var relationships = Relationships(this)

    var damages: Damages? = null

    var permissions: Permissions = Permissions(this)
    var toggleables = Toggleables()

    var raid: Raid? = null

    val channel = CivChannel()

    fun addPower(power: Int) {
        this.power += power
        CivManager.saveAsync(this)
    }

    fun removePower(power: Int) {
        if (this.power - power >= 0)
            this.power -= power
        else
            this.power = 0
        CivManager.saveAsync(this)
    }


    fun addWarp(name: String, location: Location) {
        warps[name] = location
        CivManager.saveAsync(this)
    }

    fun removeWarp(warp: String) {
        warps.remove(warp)
        CivManager.saveAsync(this)
    }


    fun addCitizen(player: CivPlayer) {
        player.civilizationInvite = null
        player.civilization = this
        citizens.add(player)
        permissions.setPlayerGroup(player, permissions.defaultRank)
        addPower(Settings.POWER_CITIZENS_WEIGHT)
        if (Settings.ADD_PLAYER_POWER_TO_CIV) {
            addPower(player.power)
        }
        player.addPower(
            CivUtil.calculateFormulaForCiv(
                Settings.POWER_CITIZEN_FORMULA,
                this
            ).toInt()
        )
        CivManager.saveAsync(this)
    }

    fun removeCitizen(player: CivPlayer) {
        player.civilization = null
        citizens.remove(player)
        permissions.playerGroupMap.remove(player.uuid)
        removePower(Settings.POWER_CITIZENS_WEIGHT)
        if (Settings.ADD_PLAYER_POWER_TO_CIV) {
            removePower(player.power)
        }
        player.removePower(
            CivUtil.calculateFormulaForCiv(
                Settings.POWER_CITIZEN_FORMULA,
                this
            ).toInt()
        )
        CivManager.saveAsync(this)
    }

    fun isPlayerOutlaw(player: CivPlayer): Boolean {
        return relationships.outlaws.contains(player)
    }

    fun isPlayerRaiding(player: CivPlayer): Boolean {
        val raid = raid ?: return false
        return raid.playersInvolved.containsKey(player)
    }

    fun canAttackCivilization(player: CivPlayer): Boolean {
        return isPlayerRaiding(player) && isPlayerLivesValid(player)
    }

    fun isPlayerToPlayerRatioValid(): Boolean {
        val raid = raid ?: return false
        return ClaimUtil.playersInCivClaims(raid.civBeingRaided, raid.civBeingRaided) / ClaimUtil.playersInCivClaims(
            raid.civBeingRaided,
            raid.civRaiding
        ) >= Settings.RAID_RATIO_MAX_IN_RAID!!
    }

    // checks if the attacking civ is attacking the attacked civ
    fun isBeingRaidedBy(attackingCivilization: Civilization?): Boolean {
        if (attackingCivilization == null) return false
        val raid = raid ?: return false
        return raid.civBeingRaided == this && raid.civRaiding == attackingCivilization
    }

    // checks if the attacking civ is attacking the attacked civ
    fun isRaiding(attackedCivilization: Civilization?): Boolean {
        if (attackedCivilization == null) return false
        val raid = raid ?: return false
        return raid.civBeingRaided == attackedCivilization && raid.civRaiding == this
    }

    fun isBeingRaidedByAllyOf(civWithAlly: Civilization?): Boolean {
        if (civWithAlly == null) return false
        val raid = raid ?: return false
        return raid.civBeingRaided == this && civWithAlly.relationships.allies.contains(
            raid.civRaiding
        )
    }

    fun isInRaid(): Boolean {
        return raid != null
    }

    private fun getRaidLives(player: CivPlayer): Int {
        return raid?.playersInvolved?.get(player) ?: 0
    }

    private fun isPlayerLivesValid(player: CivPlayer): Boolean {
        return if (Settings.RAID_LIVES == -1) true else getRaidLives(player) > 0
    }

    fun isAtWarWith(player: Player): Boolean {
        val cache = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val playerCivilization = cache.civilization
        if (playerCivilization != null) {
            return relationships.warring.contains(playerCivilization) || playerCivilization.relationships.warring.contains(
                this
            )
        }
        return false
    }

    fun addDamages(attackingCiv: Civilization, block: Block) {
        if (damages == null) damages = Damages()
        damages!!.brokenBlocksMap[block.location] = block.blockData.asString
        removePower(Settings.POWER_RAID_BLOCK)
        attackingCiv.addPower(Settings.POWER_BLOCKS_WEIGHT)
        CivManager.saveAsync(this)
        CivManager.saveAsync(attackingCiv)
    }

    fun shootBlockAndAddDamages(attackingCiv: Civilization, block: Block) {
        if (damages == null) damages = Damages()
        damages!!.brokenBlocksMap[block.location] = block.blockData.asString
        WarUtil.shootBlock(
            block,
            Vector.getRandom()
        )
        removePower(Settings.POWER_RAID_BLOCK)
        attackingCiv.addPower(Settings.POWER_BLOCKS_WEIGHT)
        CivManager.saveAsync(this)
        CivManager.saveAsync(attackingCiv)
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
            var leader: CivPlayer? = null
            try {
                leader =
                    if (io.bennyc.civilizations.db.PlayerDatastore.isStored(map.get("Leader", UUID::class.java)))
                        io.bennyc.civilizations.manager.PlayerManager.getByUUID(map.get("Leader", UUID::class.java))
                    else null
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            val home = map.getLocation("Home")
            val claims = map.get("Claims", Claims::class.java)
            val warps: Map<String, Location>? = map.getMap("Warps", String::class.java, Location::class.java)
            val citizens: MutableSet<CivPlayer> =
                map.getSet("Citizens", UUID::class.java).stream().filter(playerRemoveFilter)
                    .map { io.bennyc.civilizations.manager.PlayerManager.getByUUID(it) }
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
                return@Predicate io.bennyc.civilizations.db.PlayerDatastore.isStored(uuid)
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            false
        }
    }
}