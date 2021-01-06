/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimToggleables
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import org.mineacademy.fo.region.Region
import java.util.*
import java.util.stream.Collectors

data class Plot(val civ: Civ, val id: Int, val region: Region, var owner: CPlayer) : ConfigSerializable {
    var price = 0.0
    var forSale = false
    var members: MutableSet<CPlayer> = HashSet()
    var claimPermissions = ClaimPermissions()
    var claimToggleables = ClaimToggleables()


    fun addMember(player: CPlayer) {
        members.add(player)
        CivManager.queueForSaving(civ)
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civilization", civ.uuid)
        map.put("Region", region)
        map.put("ID", id)
        map.put("Owner", owner.uuid)
        map.put("Price", price)
        map.putIfExist("For_Sale", forSale)
        map.putIfExist("Members", members.stream().map { it.uuid }.collect(Collectors.toSet()))
        map.put("Permissions", claimPermissions)
        map.put("Toggleables", claimToggleables)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Plot {
            val civ = CivManager.getByUUID(map["Civilization", UUID::class.java])
            val region = map.get("Region", Region::class.java)
            val id = map.getInteger("ID")
            val owner = PlayerManager.getByUUID(map["Owner", UUID::class.java])
            val plot = Plot(civ, id, region, owner)
            val price = map.getInteger("Price")
            val forSale = map.getBoolean("For_Sale")
            val members: MutableSet<CPlayer> =
                map.getSet("Members", UUID::class.java).stream().map { PlayerManager.getByUUID(it) }
                    .collect(Collectors.toSet())
            val claimPermissions = map.get("Permissions", ClaimPermissions::class.java)
            val claimToggleables = map.get("Toggleables", ClaimToggleables::class.java)
            plot.price = price.toDouble()
            plot.forSale = forSale
            plot.members = members
            plot.claimPermissions = claimPermissions
            plot.claimToggleables = claimToggleables
            return plot
        }
    }
}