/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.command

import io.bennyc.civilizations.model.Region
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class MapCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "map") {
    override fun onCommand() {
        if (player.inventory.itemInMainHand.type != Material.FILLED_MAP) returnTell(io.bennyc.civilizations.settings.Localization.Warnings.INVALID_HAND_ITEM)
        val mapMeta = player.inventory.itemInMainHand.itemMeta as MapMeta
        val map = mapMeta.mapView!!
        if (map.scale != MapView.Scale.CLOSEST) returnTell(io.bennyc.civilizations.settings.Localization.Warnings.WRONG_MAP_SCALE)
        val region = Region(
            map.id,
            Location(map.world, (map.centerX - 64).toDouble(), 1.0, (map.centerZ - 64).toDouble()), Location(
                map.world,
                (map.centerX + 64).toDouble(), 1.0, (map.centerZ + 64).toDouble()
            )
        )
        map.addRenderer(io.bennyc.civilizations.MapDrawer(region))
        drawnMaps.add(map)
        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
    }

    companion object {
        val drawnMaps = mutableSetOf<MapView>()
    }

}