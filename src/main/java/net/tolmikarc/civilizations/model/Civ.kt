/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.chat.CivChannel
import net.tolmikarc.civilizations.model.impl.Bank
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.model.impl.Colony
import net.tolmikarc.civilizations.model.impl.Plot
import net.tolmikarc.civilizations.permissions.ClaimToggleables
import net.tolmikarc.civilizations.permissions.PermissionGroups
import net.tolmikarc.civilizations.war.Raid
import net.tolmikarc.civilizations.war.RegionDamages
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.model.ConfigSerializable

interface Civ : UniquelyIdentifiable, ConfigSerializable {

    var name: String?
    var power: Int
    var leader: CPlayer?

    var bank: Bank

    var home: Location?
    val claims: MutableSet<Claim>
    val colonies: MutableSet<Colony>
    val plots: MutableSet<Plot>

    var warps: MutableMap<String, Location>

    var idNumber: Int
    var totalBlocksCount: Int

    val totalClaimCount
        get() = claims.size
    val plotCount
        get() = plots.size
    val colonyCount
        get() = colonies.size

    val citizens: MutableSet<CPlayer>
    val outlaws: MutableSet<CPlayer>

    val allies: MutableSet<Civ>
    val enemies: MutableSet<Civ>

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
    var permissionGroups: PermissionGroups
    var claimToggleables: ClaimToggleables
    var raid: Raid?

    val channel: CivChannel
    
    fun addPower(power: Int)

    fun removePower(power: Int)

    fun addWarp(name: String, location: Location)

    fun removeWarp(warp: String)

    fun addBalance(amount: Double)

    fun removeBalance(amount: Double)


    fun addPlot(plot: Plot)

    fun addColony(colony: Colony)

    fun addClaim(claim: Claim)

    fun removeClaim(claim: Claim)


    fun addCitizen(player: CPlayer)

    fun removeCitizen(player: CPlayer)

    fun addAlly(ally: Civ)

    fun removeAlly(ally: Civ)

    fun addEnemy(enemy: Civ)

    fun removeEnemy(enemy: Civ)


    fun addOutlaw(player: CPlayer)

    fun removeOutlaw(player: CPlayer)

}