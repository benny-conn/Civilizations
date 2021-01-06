/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.Plot
import net.tolmikarc.civilizations.util.MathUtil.isPointInRegion
import net.tolmikarc.civilizations.util.MathUtil.isRegionInRegion
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.mineacademy.fo.region.Region
import java.util.*
import java.util.stream.Collectors

object ClaimUtil {
    fun playersInCivClaims(civilization: Civ): Int {
        var count = 0
        for (region in civilization.claims) {
            count += region.entities.stream().filter { entity: Entity? -> entity is Player }.count().toInt()
        }
        return count
    }

    fun playersInCivClaims(civWithPlayersInIt: Civ, fromThisCiv: Civ?): Int {
        var count = 0
        for (region in civWithPlayersInIt.claims) {
            for (entity in region.entities) if (entity is Player) if (fromThisCiv != null) {
                if (fromThisCiv.citizens.contains(PlayerManager.fromBukkitPlayer(entity))) count++
            }
        }
        return if (count == 0) 1 else count
    }

    fun playersInCivOnline(civilization: Civ): Int {
        var count = 0
        for (civPlayer in civilization.citizens) {
            count += if (Bukkit.getPlayer(civPlayer.uuid) != null) 1 else 0
        }
        return count
    }

    fun distanceFromNearestClaim(location: Location, civilization: Civ): Double {
        val distances: MutableList<Double> = ArrayList()
        for (region in civilization.claims) {
            distances.add(location.distance(region.center))
        }
        distances.sort()
        return distances[0]
    }

    fun distanceFromNearestClaim(location: Location): Double {
        val distances: MutableList<Double> = ArrayList()
        for (civilization in CivManager.all)
            civilization.home?.let { location.distance(it) }
                ?.let { distances.add(it) }
        distances.sort()
        return distances[0]
    }


    fun isLocationInACiv(location: Location): Boolean {
        for (civilization in CivManager.all) for (region in civilization.claims) {
            if (isPointInRegion(region, location.blockX, location.blockZ)) return true
        }
        return false
    }

    fun isLocationInRegion(location: Location, region: Region): Boolean {
        return isPointInRegion(region, location.blockX, location.blockZ)
    }

    fun isLocationInCiv(location: Location, civilization: Civ): Boolean {
        for (region in civilization.claims) {
            if (isPointInRegion(region, location.blockX, location.blockZ)) return true
        }
        return false
    }

    fun getRegionFromLocation(location: Location): Region? {
        for (civilization in CivManager.all) for (region in civilization.claims) {
            if (isPointInRegion(region, location.blockX, location.blockZ)) return region
        }
        return null
    }

    fun getPlotFromLocation(location: Location): Plot? {
        for (civilization in CivManager.all) getPlotFromLocation(location, civilization)
        return null
    }

    fun getPlotFromLocation(location: Location, civilization: Civ): Plot? {
        for (plot in civilization.plots) {
            if (isPointInRegion(plot.region, location.blockX, location.blockZ)) return plot
        }
        return null
    }

    fun getRegionFromLocation(location: Location, excludedRegion: Region): Region? {
        for (civilization in CivManager.all) for (region in civilization.claims) {
            if (region == excludedRegion) continue
            if (isPointInRegion(region, location.blockX, location.blockZ)) return region
        }
        return null
    }

    fun getRegionFromLocation(location: Location, civilization: Civ): Region? {
        for (region in civilization.claims) {
            if (isPointInRegion(region, location.blockX, location.blockZ)) return region
        }
        return null
    }

    fun getCivFromLocation(location: Location): Civ? {
        for (civilization in CivManager.all) for (region in civilization.claims) {
            if (isPointInRegion(region, location.x.toInt(), location.z.toInt())) return civilization
        }
        return null
    }


    fun regionsInSelection(region: Region, civilization: Civ): List<Region> {
        val regions: MutableList<Region> = ArrayList()
        for (claimedRegion in civilization.claims) {
            if (claimedRegion == region) continue
            if (isRegionInRegion(region, claimedRegion)) regions.add(claimedRegion)
        }
        return regions
    }

    fun regionsInSelection(region: Region): List<Region> {
        val regions: MutableList<Region> = ArrayList()
        for (civilization in CivManager.all) regions.addAll(
            regionsInSelection(
                region,
                civilization
            )
        )
        return regions
    }

    fun plotsInSelection(region: Region?): List<Plot> {
        val plots: MutableList<Plot> = ArrayList()
        for (civilization in CivManager.all) for (plot in civilization.plots) {
            if (isRegionInRegion(plot.region, region!!)) plots.add(plot)
        }
        return plots
    }

    fun isRegionConnected(region: Region, civilization: Civ): Boolean {
        val squareBox1 = region.boundingBox.stream().filter { location: Location -> location.blockY == 1 }
            .collect(Collectors.toSet())
        for (claimedRegion in civilization.claims) {
            val squareBox2 = claimedRegion.boundingBox.stream().filter { location: Location -> location.blockY == 1 }
                .collect(Collectors.toSet())
            if (squareBox1.stream().anyMatch { o: Location -> squareBox2.contains(o) }) return true
        }
        return false
    }


    fun isLocationConnected(location: Location, civilization: Civ, excludedRegion: Region): Boolean {
        for (claimedRegion in civilization.claims) {
            if (claimedRegion == excludedRegion) continue
            val squareBox1 = claimedRegion.boundingBox.stream().filter { location1: Location -> location1.blockY == 1 }
                .collect(Collectors.toSet())
            for (borderLocation in squareBox1) {
                if (location.x == borderLocation.x && location.z == borderLocation.z) return true
            }
        }
        return false

    }
}