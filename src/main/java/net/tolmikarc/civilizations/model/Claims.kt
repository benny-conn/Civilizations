/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*
import kotlin.math.abs

class Claims(val civ: Civilization) : ConfigSerializable {


    val claims = mutableSetOf<Region>()
    val colonies = mutableSetOf<Colony>()
    val plots = mutableSetOf<Plot>()

    var totalBlocksCount = 0
    var idNumber = 1


    val totalClaimCount
        get() = claims.size
    val plotCount
        get() = plots.size
    val colonyCount
        get() = colonies.size

    fun addPlot(plot: Plot) {
        idNumber++
        plots.add(plot)
        CivManager.saveAsync(civ)
    }

    fun addColony(colony: Colony) {
        colony.id = idNumber
        idNumber++
        colonies.add(colony)
        CivManager.saveAsync(civ)
    }

    fun removePlot(plot: Plot) {
        plots.remove(plot)
        CivManager.saveAsync(civ)
    }

    fun removeColony(colony: Colony) {
        colonies.remove(colony)
        CivManager.saveAsync(civ)
    }

    fun addClaim(region: Region) {
        claims.add(region)
        idNumber++
        val amount = net.tolmikarc.civilizations.util.MathUtil.areaBetweenTwoPoints(
            region.primary,
            region.secondary
        )
        addTotalBlocks(amount)
        CivManager.saveAsync(civ)
    }


    fun removeClaim(region: Region) {
        claims.remove(region)
        val area = net.tolmikarc.civilizations.util.MathUtil.areaBetweenTwoPoints(
            region.primary,
            region.secondary
        )
        removeTotalBlocks(area)
        CivManager.saveAsync(civ)
    }

    private fun addTotalBlocks(amount: Int) {
        totalBlocksCount += amount
        civ.addPower(Settings.POWER_BLOCKS_WEIGHT * amount)
    }

    private fun removeTotalBlocks(amount: Int) {
        if (totalBlocksCount - amount < 0) {
            totalBlocksCount = 0
            val newAmount = abs(0 - totalBlocksCount)
            civ.removePower(Settings.POWER_BLOCKS_WEIGHT * newAmount)
        } else {
            totalBlocksCount -= amount
            civ.removePower(Settings.POWER_BLOCKS_WEIGHT * amount)
        }
    }


    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civ", civ.uuid)
        map.put("Claims", claims)
        map.put("Colonies", colonies)
        map.put("Plots", plots)
        map.put("Total_Blocks", totalBlocksCount)
        map.put("ID_Number", idNumber)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Claims {
            val claims = Claims(CivManager.getByUUID(map.get("Civ", UUID::class.java)))
            claims.claims.addAll(map.getSet("Claims", Region::class.java))
            claims.colonies.addAll(map.getSet("Colonies", Colony::class.java))
            claims.plots.addAll(map.getSet("Plots", Plot::class.java))
            claims.idNumber = map.getInteger("ID_Number")
            claims.totalBlocksCount = abs(map.getInteger("Total_blocks"))
            return claims
        }
    }

}