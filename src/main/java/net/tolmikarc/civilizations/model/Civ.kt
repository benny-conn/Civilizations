/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.chat.CivChannel
import net.tolmikarc.civilizations.model.impl.Bank
import net.tolmikarc.civilizations.model.impl.Claims
import net.tolmikarc.civilizations.model.impl.Relationships
import net.tolmikarc.civilizations.permissions.PermissionGroups
import net.tolmikarc.civilizations.permissions.Toggleables
import net.tolmikarc.civilizations.war.Damages
import net.tolmikarc.civilizations.war.Raid
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.model.ConfigSerializable

interface Civ : UniquelyIdentifiable, ConfigSerializable {

    var name: String?
    var description: String?
    var power: Int
    var leader: CPlayer?

    var bank: Bank

    var home: Location?
    var claims: Claims

    var warps: MutableMap<String, Location>

    val citizens: MutableSet<CPlayer>

    var relationships: Relationships


    val citizenCount
        get() = citizens.size

    var damages: Damages?
    var banner: ItemStack?
    var book: ItemStack?
    var permissionGroups: PermissionGroups
    var toggleables: Toggleables
    var raid: Raid?

    val channel: CivChannel

    fun addPower(power: Int)

    fun removePower(power: Int)

    fun addWarp(name: String, location: Location)

    fun removeWarp(warp: String)

    fun addCitizen(player: CPlayer)

    fun removeCitizen(player: CPlayer)


}