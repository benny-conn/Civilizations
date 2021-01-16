/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations

import net.tolmikarc.civilizations.constants.Constants
import net.tolmikarc.civilizations.model.Civ

object DynmapHook {

    // TODO figure out how to make this work

    private val dynmapHook
        get() = CivilizationsPlugin.dynmapApi
    private val markerApi
        get() = dynmapHook?.markerAPI

    fun doDynmapStuffWithCiv(civilization: Civ) {
        val xcorners = DoubleArray(2).apply {
            this[0] = civilization.home?.x!!
            this[1] = civilization.home!!.x + 10
        }
        val zcorners = DoubleArray(2).apply {
            this[0] = civilization.home?.z!!
            this[1] = civilization.home!!.z + 10
        }
        val markertset =
            markerApi!!.createMarkerSet(Constants.DYNMAP_ID, civilization.name, markerApi!!.markerIcons, false)
        val areaMarker = markertset.createAreaMarker(
            Constants.DYNMAP_ID, civilization.name, true,
            civilization.home!!.world.toString(),
            xcorners, zcorners, false
        )
        areaMarker.setFillStyle(1.0, 0x42f4f1)
    }

}