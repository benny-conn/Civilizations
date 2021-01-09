/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.adapter

import com.massivecraft.factions.FLocation
import com.massivecraft.factions.FPlayer
import com.massivecraft.factions.Faction
import com.massivecraft.factions.perms.Role
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Civilization
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.permissions.PermissionGroups
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import java.util.*

object FactionsUUIDAdapter {
    private val convertedFactions: MutableMap<Faction, Civ> = HashMap()

    fun convertFactionToCiv(faction: Faction, deleteAfterConversion: Boolean): Civ {
        val civ = Civilization(UUID.randomUUID())
        civ.name = faction.tag
        civ.leader = PlayerManager.getByUUID(faction.getFPlayersWhereRole(Role.ADMIN)[0].player.uniqueId)
        civ.permissionGroups = convertPermissions(faction)
        civ.claims.addAll(getConvertedRegions(faction, civ))
        civ.citizens.addAll(faction.fPlayers.map { fPlayer: FPlayer -> PlayerManager.getByUUID(fPlayer.player.uniqueId) })
        civ.idNumber = civ.totalClaimCount + 1
        civ.power = faction.power.toInt()
        civ.home = faction.home
        val book = ItemStack(Material.WRITTEN_BOOK)
        val bookMeta = book.itemMeta as BookMeta?
        bookMeta!!.addPage("Civ: " + civ.name.toString() + " Description: " + faction.description)
        book.itemMeta = bookMeta
        civ.book = book
        convertedFactions[faction] = civ
        if (deleteAfterConversion) faction.remove()
        return civ
    }

    private fun getConvertedRegions(faction: Faction, civ: Civ): MutableSet<Claim> {
        val handledFactionLocations: MutableSet<FLocation> = HashSet()
        val newRegions: MutableSet<Claim> = HashSet()
        var id = 0
        for (fLocation in faction.allClaims) {
            if (handledFactionLocations.contains(fLocation)) continue
            var lowestLocation = fLocation
            var highestLocation = fLocation
            while (faction.allClaims.contains(highestLocation.getRelative(1, 0))) {
                val nextFLocation = highestLocation.getRelative(1, 0)
                highestLocation = nextFLocation
                handledFactionLocations.add(nextFLocation)
            }
            while (faction.allClaims.contains(lowestLocation.getRelative(0, 1))) {
                val nextFLocation = lowestLocation.getRelative(0, 1)
                val handledLocationsMovingRight: MutableSet<FLocation> = HashSet()
                while (faction.allClaims.contains(nextFLocation.getRelative(1, 0))) {
                    val nextNextFLocation = nextFLocation.getRelative(1, 0)
                    handledLocationsMovingRight.add(nextNextFLocation)
                    if (nextNextFLocation == highestLocation.getRelative(0, 1)) break
                }
                if (nextFLocation != highestLocation.getRelative(0, 1)) break
                highestLocation = nextFLocation.getRelative(0, 1)
                lowestLocation = lowestLocation.getRelative(0, 1)
                handledFactionLocations.add(nextFLocation)
                handledFactionLocations.addAll(handledLocationsMovingRight)
            }
            lowestLocation = fLocation
            val newRegion = Claim(
                id,
                Location(
                    lowestLocation.world,
                    (lowestLocation.x * 16).toDouble(),
                    0.0,
                    (lowestLocation.z * 16).toDouble()
                ),
                Location(
                    highestLocation.world,
                    (highestLocation.x * 16 + 15).toDouble(),
                    256.0,
                    (highestLocation.z * 16 + 15).toDouble()
                )
            )
            newRegions.add(newRegion)
            id++
        }
        return newRegions
    }


    private fun convertPermissions(faction: Faction): PermissionGroups {
        TODO()
    }
}