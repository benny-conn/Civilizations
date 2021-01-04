/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.hook

import net.tolmikarc.civilizations.CivilizationsPlugin
import net.tolmikarc.civilizations.constants.Constants
import net.tolmikarc.civilizations.model.Civilization

object DynmapHook {
    private val dynmapHook
        get() = CivilizationsPlugin.dynmapApi
    private val markerApi
        get() = dynmapHook?.markerAPI

    fun doDynmapStuffWithCiv(civilization: Civilization) {
        val xcorners = DoubleArray(2).apply {
            this[0] = civilization.home?.x!!
            this[1] = civilization.home!!.x + 10
        }
        val zcorners = DoubleArray(2).apply {
            this[0] = civilization.home?.z!!
            this[1] = civilization.home!!.z + 10
        }
        var markertset =
            markerApi!!.createMarkerSet(Constants.DYNMAP_ID, civilization.name, markerApi!!.markerIcons, false)
        var areaMarker = markertset.createAreaMarker(
            Constants.DYNMAP_ID, civilization.name, true,
            civilization.home!!.world.toString(),
            xcorners, zcorners, false
        )
        areaMarker.setFillStyle(1.0, 0x42f4f1)
    }

}