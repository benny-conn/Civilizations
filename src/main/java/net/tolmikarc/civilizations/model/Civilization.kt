/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.api.Civ
import net.tolmikarc.civilizations.db.CivDatastore
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimToggleables
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.war.Raid
import net.tolmikarc.civilizations.war.RegionDamages
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.Common
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import org.mineacademy.fo.region.Region
import java.sql.SQLException
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.collections.HashSet
import kotlin.collections.set

data class Civilization(val uuid: UUID) : ConfigSerializable, Civ {
    override var name: String? = null
        set(value) {
            byName[value?.toLowerCase()] = this
            field = value
        }
    override var power = 0
    override var leader: CivPlayer? = null
    override var bank: CivBank = CivBank(this)
    override var home: Location? = null
    override var claims: MutableSet<Region> = HashSet()
    override var colonies: MutableSet<CivColony> = HashSet()
    override var plots: MutableSet<CivPlot> = HashSet()
    override var warps: MutableMap<String, Location> = LinkedHashMap()
    override var idNumber = 1
    override var totalBlocksCount = 0

    override val totalClaimCount
        get() = claims.size
    override val plotCount
        get() = plots.size
    override val colonyCount
        get() = colonies.size

    override var citizens: MutableSet<CivPlayer> = HashSet()
    override var officials: MutableSet<CivPlayer> = HashSet()
    override var outlaws: MutableSet<CivPlayer> = HashSet()
    override var allies: MutableSet<Civilization> = HashSet()
    override var enemies: MutableSet<Civilization> = HashSet()

    override val warring: Set<Civilization>
        get() {
            val set: MutableSet<Civilization> = HashSet()
            for (civ in enemies) {
                if (civ.enemies.contains(this))
                    set.add(civ)
            }
            return set
        }

    override val citizenCount
        get() = citizens.size

    override var regionDamages: RegionDamages? = null

    override var banner: ItemStack? = null
        set(value) {
            val singleBanner = if (value != null) ItemStack(value) else ItemStack(Material.BLUE_BANNER)
            singleBanner.amount = 1
            saveAsync(this)
            field = value
        }

    override var book: ItemStack? = null
        set(value) {
            val singleBook = if (value != null) ItemStack(value) else ItemStack(Material.BOOK)
            singleBook.amount = 1
            saveAsync(this)
            field = value
        }


    override var claimPermissions = ClaimPermissions()
    override var claimToggleables = ClaimToggleables()
    override var raid: Raid? = null

    override fun addPower(power: Int) {
        this.power += power
        queueForSaving()
    }

    override fun removePower(power: Int) {
        if (this.power - power >= 0)
            this.power -= power
        else
            this.power = 0
        queueForSaving()
    }

    private fun addTotalBlocks(amount: Int) {
        totalBlocksCount += amount
        addPower(Settings.POWER_BLOCKS_WEIGHT * amount)
    }

    private fun removeTotalBlocks(amount: Int) {
        totalBlocksCount -= amount
        removePower(Settings.POWER_BLOCKS_WEIGHT * amount)
    }


    override fun addWarp(name: String, location: Location) {
        warps[name] = location
        saveAsync(this)
    }

    override fun removeWarp(warp: String) {
        warps.remove(warp)
        saveAsync(this)
    }

    override fun addBalance(amount: Double) {
        bank.addBalance(amount)
    }

    override fun removeBalance(amount: Double) {
        bank.removeBalance(amount)
    }


    override fun addPlot(plot: CivPlot) {
        plot.owner = leader ?: return
        idNumber++
        plots.add(plot)
        saveAsync(this)
    }

    override fun addColony(colony: CivColony) {
        colony.id = idNumber
        idNumber++
        colonies.add(colony)
        saveAsync(this)
    }

    override fun addClaim(region: Region) {
        claims.add(region)
        idNumber++
        val amount = net.tolmikarc.civilizations.util.MathUtil.areaBetweenTwoPoints(
            region.primary,
            region.secondary
        )
        addTotalBlocks(amount)
        saveAsync(this)
    }


    override fun removeClaim(region: Region) {
        claims.remove(region)
        val area = net.tolmikarc.civilizations.util.MathUtil.areaBetweenTwoPoints(
            region.primary,
            region.secondary
        )
        removeTotalBlocks(area)
        saveAsync(this)
    }


    override fun addOfficial(player: CivPlayer) {
        officials.add(player)
        player.addPower(CivUtil.calculateFormulaForCiv(Settings.POWER_OFFICIAL_FORMULA, this).toInt())
        saveAsync(this)
    }

    override fun removeOfficial(player: CivPlayer) {
        officials.remove(player)
        player.removePower(CivUtil.calculateFormulaForCiv(Settings.POWER_OFFICIAL_FORMULA, this).toInt())
        saveAsync(this)
    }

    override fun addCitizen(player: CivPlayer) {
        citizens.add(player)
        addPower(Settings.POWER_CITIZENS_WEIGHT)
        if (Settings.ADD_PLAYER_POWER_TO_CIV) {
            addPower(player.power)
        }
        player.addPower(CivUtil.calculateFormulaForCiv(Settings.POWER_CITIZEN_FORMULA, this).toInt())
        saveAsync(this)
    }

    override fun removeCitizen(player: CivPlayer) {
        citizens.remove(player)
        removePower(Settings.POWER_CITIZENS_WEIGHT)
        if (Settings.ADD_PLAYER_POWER_TO_CIV) {
            removePower(player.power)
        }
        player.removePower(CivUtil.calculateFormulaForCiv(Settings.POWER_CITIZEN_FORMULA, this).toInt())
        saveAsync(this)
    }

    override fun addAlly(ally: Civilization) {
        allies.add(ally)
        saveAsync(this)
    }

    override fun removeAlly(ally: Civilization) {
        allies.remove(ally)
        saveAsync(this)
    }

    override fun addEnemy(enemy: Civilization) {
        enemies.add(enemy)
        saveAsync(this)
    }

    override fun removeEnemy(enemy: Civilization) {
        enemies.remove(enemy)
        saveAsync(this)
    }


    override fun addOutlaw(player: CivPlayer) {
        outlaws.add(player)
        saveAsync(this)
    }

    override fun removeOutlaw(player: CivPlayer) {
        outlaws.remove(player)
        saveAsync(this)
    }

    override fun removeCivilization() {
        for (civ in civilizationsMap.values) {
            civ.allies.remove(this)
            civ.enemies.remove(this)
        }
        civilizationsMap.remove(uuid)
        QUEUED_FOR_SAVING.remove(this)
        byName.remove(name)
        CivDatastore.delete(uuid)
    }

    fun queueForSaving() {
        QUEUED_FOR_SAVING.add(this)
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.putIfExist("UUID", uuid)
        map.putIfExist("Name", name)
        map.putIfExist("Power", power)
        map.putIfExist("Leader", leader?.playerUUID)
        map.putIfExist("Home", home)
        map.putIfExist("Claims", claims)
        map.putIfExist("Plots", plots)
        map.putIfExist("Warps", warps)
        map.putIfExist("Claim_Number", idNumber)
        map.putIfExist("Total_Claim_Count", totalClaimCount)
        map.putIfExist("Total_Blocks_Count", totalBlocksCount)
        map.putIfExist("Plot_Count", plotCount)
        map.putIfExist(
            "Officials",
            officials.stream().map { obj: CivPlayer -> obj.playerUUID }.collect(Collectors.toSet())
        )
        map.putIfExist(
            "Citizens",
            citizens.stream().map { obj: CivPlayer -> obj.playerUUID }.collect(Collectors.toSet())
        )
        map.putIfExist("Allies", allies.stream().map { obj: Civilization -> obj.uuid }.collect(Collectors.toSet()))
        map.putIfExist("Enemies", enemies.stream().map { obj: Civilization -> obj.uuid }.collect(Collectors.toSet()))
        map.putIfExist("Outlaws", outlaws.stream().map { obj: CivPlayer -> obj.playerUUID }.collect(Collectors.toSet()))
        map.putIfExist("Bank", bank)
        map.putIfExist("Banner", banner)
        map.putIfExist("Book", book)
        map.putIfExist("Claim_Permissions", claimPermissions)
        map.putIfExist("Claim_Toggleables", claimToggleables)
        map.putIfExist("Region_Damages", regionDamages)
        return map
    }

    companion object {
        private val QUEUED_FOR_SAVING: MutableSet<Civilization> = HashSet()
        val civilizationsMap: MutableMap<UUID, Civilization> = HashMap()
        private val byName: MutableMap<String?, Civilization> = HashMap()

        private fun initializeCiv(uuid: UUID): Civilization {
            val civilization = Civilization(uuid)
            civilizationsMap[uuid] = civilization
            return civilization
        }

        fun createCiv(name: String, player: CivPlayer): Civilization {
            val uuid = UUID.randomUUID()
            val civilization = initializeCiv(uuid)
            civilization.name = name
            civilization.leader = player
            civilization.addCitizen(player)
            player.civilization = civilization
            player.power += CivUtil.calculateFormulaForCiv(Settings.POWER_LEADER_FORMULA, civilization).toInt()
            player.power += CivUtil.calculateFormulaForCiv(Settings.POWER_CITIZEN_FORMULA, civilization).toInt()
            byName[name] = civilization
            QUEUED_FOR_SAVING.add(civilization)
            return civilization
        }


        fun createCiv(civilization: Civilization): Civilization {
            civilizationsMap[civilization.uuid] = civilization
            byName[civilization.name] = civilization
            QUEUED_FOR_SAVING.add(civilization)
            return civilization
        }


        fun fromUUID(uuid: UUID): Civilization {
            var civilization = civilizationsMap[uuid]
            if (civilization == null) {
                civilization = initializeCiv(uuid)
                loadAsync(civilization)
            }
            return civilization
        }


        fun fromName(name: String): Civilization? {
            return byName[name.toLowerCase()]
        }


        val civNames: MutableSet<String?>
            get() = byName.keys

        private fun load(civ: Civilization) {
            CivDatastore.load(civ)
        }

        private fun loadAsync(civ: Civilization) {
            Common.runLaterAsync { load(civ) }
        }

        private fun save(civilization: Civilization) {
            CivDatastore.save(civilization)
        }


        fun saveAsync(civilization: Civilization) {
            Common.runLaterAsync { save(civilization) }
        }


        fun saveQueuedForSaving() {
            for (civilization in QUEUED_FOR_SAVING) {
                save(civilization)
            }
        }

        @JvmStatic
        fun deserialize(map: SerializedMap): Civilization {
            val uuid = map.get("UUID", UUID::class.java)
            val cache = Civilization(uuid)
            val name = map.getString("Name")
            val power = map.getInteger("Power")
            var leader: CivPlayer? = null
            try {
                leader =
                    if (PlayerDatastore.isStored(map.get("Leader", UUID::class.java)))
                        CivPlayer.fromUUID(map.get("Leader", UUID::class.java))
                    else null
            } catch (e: SQLException) {
                e.printStackTrace()
            }
            val home = map.getLocation("Home")
            val claims = map.getSet("Claims", Region::class.java)
            val plots = map.getSet("Plots", CivPlot::class.java)
            val warps: Map<String, Location>? = map.getMap("Warps", String::class.java, Location::class.java)
            val claimNumber = map.getInteger("Claim_Number")
            val totalBlocksCount = map.getInteger("Total_Blocks_Count")
            val officials: MutableSet<CivPlayer> =
                map.getSet("Officials", UUID::class.java).stream().filter(playerRemoveFilter).map(CivPlayer::fromUUID)
                    .collect(Collectors.toSet())
            val citizens: MutableSet<CivPlayer> =
                map.getSet("Citizens", UUID::class.java).stream().filter(playerRemoveFilter).map(CivPlayer::fromUUID)
                    .collect(Collectors.toSet())
            val allies: MutableSet<Civilization> =
                map.getSet("Allies", UUID::class.java).stream().map(::fromUUID).collect(Collectors.toSet())
            val enemies: MutableSet<Civilization> =
                map.getSet("Enemies", UUID::class.java).stream().map(::fromUUID).collect(Collectors.toSet())
            val outlaws: MutableSet<CivPlayer> =
                map.getSet("Outlaws", UUID::class.java).stream().filter(playerRemoveFilter).map(CivPlayer::fromUUID)
                    .collect(Collectors.toSet())
            val bank = map.get("Bank", CivBank::class.java)
            val banner = map.getItem("Banner")
            val book = map.getItem("Book")
            val permissions = map.get("Claim_Permissions", ClaimPermissions::class.java)
            val toggleables = map.get("Claim_Toggleables", ClaimToggleables::class.java)
            val regionDamages = map.get("Region_Damages", RegionDamages::class.java)
            cache.name = name
            if (power != null) cache.power = power
            if (leader == null) leader = citizens.iterator().next()
            cache.leader = leader
            if (home != null) cache.home = home
            if (claims != null) cache.claims = claims
            if (plots != null) cache.plots = plots
            if (warps != null) cache.warps = warps as MutableMap<String, Location>
            if (claimNumber != null) cache.idNumber = claimNumber
            if (totalBlocksCount != null) cache.totalBlocksCount = totalBlocksCount
            cache.officials = officials
            cache.citizens = citizens
            cache.allies = allies
            cache.enemies = enemies
            cache.outlaws = outlaws
            if (bank != null) cache.bank = bank
            if (banner != null) cache.banner = banner
            if (book != null) cache.book = book
            if (permissions != null) cache.claimPermissions = permissions
            if (toggleables != null) cache.claimToggleables = toggleables
            if (regionDamages != null) cache.regionDamages = regionDamages
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