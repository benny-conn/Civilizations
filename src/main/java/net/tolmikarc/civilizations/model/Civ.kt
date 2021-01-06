/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimToggleables
import net.tolmikarc.civilizations.war.Raid
import net.tolmikarc.civilizations.war.RegionDamages
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.model.ConfigSerializable
import org.mineacademy.fo.region.Region

interface Civ : UniquelyIdentifiable, ConfigSerializable {

    var name: String?
    var power: Int
    var leader: CPlayer?
    var bank: CivBank
    var home: Location?
    var claims: MutableSet<Region>
    var colonies: MutableSet<CivColony>
    var plots: MutableSet<CivPlot>
    var warps: MutableMap<String, Location>
    var idNumber: Int
    var totalBlocksCount: Int

    val totalClaimCount
        get() = claims.size
    val plotCount
        get() = plots.size
    val colonyCount
        get() = colonies.size

    var citizens: MutableSet<CPlayer>
    var officials: MutableSet<CPlayer>
    var outlaws: MutableSet<CPlayer>
    var allies: MutableSet<Civ>
    var enemies: MutableSet<Civ>

    val warring: Set<Civ>
        get() {
            val set: MutableSet<Civ> = HashSet()
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


    fun addPower(power: Int)

    fun removePower(power: Int)

    fun addWarp(name: String, location: Location)

    fun removeWarp(warp: String)

    fun addBalance(amount: Double)

    fun removeBalance(amount: Double)


    fun addPlot(plot: CivPlot)

    fun addColony(colony: CivColony)

    fun addClaim(region: Region)

    fun removeClaim(region: Region)

    fun addOfficial(player: CPlayer)

    fun removeOfficial(player: CPlayer)

    fun addCitizen(player: CPlayer)

    fun removeCitizen(player: CPlayer)

    fun addAlly(ally: Civ)

    fun removeAlly(ally: Civ)

    fun addEnemy(enemy: Civ)

    fun removeEnemy(enemy: Civ)


    fun addOutlaw(player: CPlayer)

    fun removeOutlaw(player: CPlayer)
}