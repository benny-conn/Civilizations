/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.MapDrawer
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.settings.Localization
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class MapCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "map") {
    override fun onCommand() {
        if (player.inventory.itemInMainHand.type != Material.FILLED_MAP) returnTell(Localization.Warnings.INVALID_HAND_ITEM)
        val mapMeta = player.inventory.itemInMainHand.itemMeta as MapMeta
        val map = mapMeta.mapView!!
        if (map.scale != MapView.Scale.CLOSEST) returnTell(Localization.Warnings.WRONG_MAP_SCALE)
        val region = Claim(
            map.id,
            Location(map.world, (map.centerX - 64).toDouble(), 1.0, (map.centerZ - 64).toDouble()), Location(
                map.world,
                (map.centerX + 64).toDouble(), 1.0, (map.centerZ + 64).toDouble()
            )
        )
        map.addRenderer(MapDrawer(region))
        drawnMaps.add(map)
        tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
    }

    companion object {
        val drawnMaps = mutableSetOf<MapView>()
    }

}