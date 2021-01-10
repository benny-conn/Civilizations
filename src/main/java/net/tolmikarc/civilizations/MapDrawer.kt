/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.util.ClaimUtil
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapPalette
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import kotlin.math.abs

class MapDrawer(val region: Claim) : MapRenderer() {
    override fun render(map: MapView, canvas: MapCanvas, player: Player) {
        AsyncEnvironment.run {
            val civPlayer = PlayerManager.fromBukkitPlayer(player)
            val playersCiv = civPlayer.civilization
            for (location in region.blocks.map { it.location })
                if (ClaimUtil.isLocationInACiv(location)) {
                    val civ = ClaimUtil.getCivFromLocation(location)!!
                    var color = MapPalette.BLUE
                    if (playersCiv != null) {
                        if (playersCiv.enemies.contains(civ))
                            color = MapPalette.RED
                        if (playersCiv.allies.contains(civ))
                            color = MapPalette.LIGHT_GREEN
                    }
                    canvas.setPixel(
                        convertLocation(location, region).x.toInt(),
                        convertLocation(location, region).z.toInt(),
                        color
                    )
                }
        }
    }

    private fun convertLocation(location: Location, region: Claim): Location {
        val xoffset = region.center.x - 64
        val zoffset = region.center.z - 64

        return Location(location.world, abs(location.x - xoffset), 0.0, abs(location.z - zoffset))
    }

}