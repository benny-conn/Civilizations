/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.api

import net.tolmikarc.civilizations.model.*
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimToggleables
import net.tolmikarc.civilizations.war.Raid
import net.tolmikarc.civilizations.war.RegionDamages
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.region.Region

interface Civ {

    var name: String?
    var power: Int
    var leader: CivPlayer?
    var bank: Bank
    var home: Location?
    var claims: MutableSet<Region>
    var colonies: MutableSet<Colony>
    var plots: MutableSet<Plot>
    var warps: MutableMap<String, Location>
    var idNumber: Int
    var totalBlocksCount: Int

    val totalClaimCount
        get() = claims.size
    val plotCount
        get() = plots.size
    val colonyCount
        get() = colonies.size

    var citizens: MutableSet<CivPlayer>
    var officials: MutableSet<CivPlayer>
    var outlaws: MutableSet<CivPlayer>
    var allies: MutableSet<Civilization>
    var enemies: MutableSet<Civilization>

    val warring: Set<Civilization>
        get() {
            val set: MutableSet<Civilization> = HashSet()
            for (civ in enemies) {
                if (civ.enemies.contains(this))
                    set.add(civ)
            }
            return set
        }

    val citizenCount
        get() = citizens.size

    var regionDamages: RegionDamages?
    var banner: ItemStack?
    var book: ItemStack?
    var claimPermissions: ClaimPermissions
    var claimToggleables: ClaimToggleables
    var raid: Raid?


    fun addPower(amount: Int)

    fun removePower(amount: Int)

    fun addWarp(name: String, location: Location)

    fun removeWarp(warp: String)

    fun addBalance(amount: Double)

    fun removeBalance(amount: Double)


    fun addPlot(plot: Plot)

    fun addColony(colony: Colony)

    fun addClaim(region: Region)

    fun removeClaim(region: Region)

    fun addOfficial(player: CivPlayer)

    fun removeOfficial(player: CivPlayer)

    fun addCitizen(player: CivPlayer)

    fun removeCitizen(player: CivPlayer)

    fun addAlly(ally: Civilization)

    fun removeAlly(ally: Civilization)

    fun addEnemy(enemy: Civilization)

    fun removeEnemy(enemy: Civilization)


    fun addOutlaw(player: CivPlayer)

    fun removeOutlaw(player: CivPlayer)

    fun removeCivilization()
}