/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.util

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.model.Plot
import io.bennyc.civilizations.model.Region
import io.bennyc.civilizations.util.MathUtil.isRegionInRegion
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

object ClaimUtil {

  
    fun playersInCivClaims(civilization: Civilization): Int {
        var count = 0
        for (region in civilization.claims.claims) {
            count += region.entities.stream().filter { entity: Entity? -> entity is Player }.count().toInt()
        }
        return count
    }

    fun playersInCivClaims(civWithPlayersInIt: Civilization, fromThisCiv: Civilization?): Int {
        var count = 0
        for (region in civWithPlayersInIt.claims.claims) {
            for (entity in region.entities) if (entity is Player) if (fromThisCiv != null) {
                if (fromThisCiv.citizens.contains(io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(entity))) count++
            }
        }
        return if (count == 0) 1 else count
    }

    fun playersInCivOnline(civilization: Civilization): Int {
        var count = 0
        for (civPlayer in civilization.citizens) {
            count += if (Bukkit.getPlayer(civPlayer.uuid) != null) 1 else 0
        }
        return count
    }

    fun distanceFromNearestClaim(location: Location, civilization: Civilization): Double {
        val distances: MutableList<Double> = ArrayList()
        for (region in civilization.claims.claims) {
            distances.add(location.distance(region.center))
        }
        distances.sort()
        return distances[0]
    }

    fun distanceFromNearestClaim(location: Location): Double {
        val distances: MutableList<Double> = ArrayList()
        for (civilization in io.bennyc.civilizations.manager.CivManager.all)
            civilization.home?.let { location.distance(it) }
                ?.let { distances.add(it) }
        distances.sort()
        return distances[0]
    }


    fun isLocationInACiv(location: Location): Boolean {
        for (civilization in io.bennyc.civilizations.manager.CivManager.all) for (region in civilization.claims.claims) {
            if (region.isWithin(location)) return true
        }
        return false
    }


    fun isLocationInCiv(location: Location, civilization: Civilization): Boolean {
        for (region in civilization.claims.claims) {
            if (region.isWithin(location)) return true
        }
        return false
    }

    fun getRegionFromLocation(location: Location): Region? {
        for (civilization in io.bennyc.civilizations.manager.CivManager.all) for (region in civilization.claims.claims) {
            if (region.isWithin(location)) return region
        }
        return null
    }

    fun getPlotFromLocation(location: Location): Plot? {
        for (civilization in io.bennyc.civilizations.manager.CivManager.all) getPlotFromLocation(location, civilization)
        return null
    }

    fun getPlotFromLocation(location: Location, civilization: Civilization): Plot? {
        for (plot in civilization.claims.plots) {
            if (plot.region.isWithin(location)) return plot
        }
        return null
    }

    fun getRegionFromLocation(location: Location, excludedRegion: Region): Region? {
        for (civilization in io.bennyc.civilizations.manager.CivManager.all) for (region in civilization.claims.claims) {
            if (region == excludedRegion) continue
            if (region.isWithin(location)) return region
        }
        return null
    }

    fun getRegionFromLocation(location: Location, civilization: Civilization): Region? {
        for (region in civilization.claims.claims) {
            if (region.isWithin(location)) return region
        }
        return null
    }

    fun getCivFromLocation(location: Location): Civilization? {
        for (civilization in io.bennyc.civilizations.manager.CivManager.all) for (region in civilization.claims.claims) {
            if (region.isWithin(location)) return civilization
        }
        return null
    }


    private fun regionsInSelection(region: Region, civilization: Civilization): List<Region> {
        val regions: MutableList<Region> = ArrayList()
        for (claimedRegion in civilization.claims.claims) {
            if (claimedRegion == region) continue
            if (isRegionInRegion(region, claimedRegion)) regions.add(claimedRegion)
        }
        return regions
    }

    fun regionsInSelection(region: Region): List<Region> {
        val regions: MutableList<Region> = ArrayList()
        for (civilization in io.bennyc.civilizations.manager.CivManager.all) regions.addAll(
            regionsInSelection(
                region,
                civilization
            )
        )
        return regions
    }

    fun plotsInSelection(region: Region?): List<Plot> {
        val plots: MutableList<Plot> = ArrayList()
        for (civilization in io.bennyc.civilizations.manager.CivManager.all) for (plot in civilization.claims.plots) {
            if (isRegionInRegion(plot.region, region!!)) plots.add(plot)
        }
        return plots
    }

    fun isRegionConnected(region: Region, civilization: Civilization): Boolean {
        val boundingBox = region.boundingBox.filter { it.y == 1.0 }
        for (claimedRegion in civilization.claims.claims) {
            val borderingBoundingBox = claimedRegion.boundingBox.filter { it.y == 1.0 }
            if (borderingBoundingBox.any { boundingBox.contains(it) }) return true
        }
        return false
    }


}