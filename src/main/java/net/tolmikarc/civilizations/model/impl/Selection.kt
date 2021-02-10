/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model.impl

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.mineacademy.fo.Common

class Selection {
    var primary: Location? = null
        private set
    var secondary: Location? = null
        private set
    var lastSelection: SelectionType? = null
        private set

    fun select(block: Block, player: Player, clickType: ClickType) {
        when (clickType) {
            ClickType.LEFT -> {
                if (primary != null)
                    player.sendBlockChange(primary!!, Bukkit.createBlockData(primary!!.block.type))
                primary = block.location
            }
            ClickType.RIGHT -> {
                if (secondary != null)
                    player.sendBlockChange(secondary!!, Bukkit.createBlockData(secondary!!.block.type))
                secondary = block.location
            }
        }
        Common.runLaterAsync(5) {
            player.sendBlockChange(block.location, Bukkit.createBlockData(Material.DIAMOND_BLOCK))
        }
    }

    fun selectNoClickType(block: Block, player: Player): SelectionType {
        var selectionType: SelectionType = SelectionType.PRIMARY
        when {
            primary == null -> {
                primary = block.location
                selectionType = SelectionType.PRIMARY
            }
            secondary == null -> {
                secondary = block.location
                selectionType = SelectionType.SECONDARY
            }
            lastSelection == SelectionType.PRIMARY -> {
                secondary = block.location
                selectionType = SelectionType.SECONDARY
            }
            lastSelection == SelectionType.SECONDARY -> {
                primary = block.location
                selectionType = SelectionType.PRIMARY
            }
        }
        lastSelection = selectionType

        Common.runLaterAsync(5) {
            player.sendBlockChange(block.location, Bukkit.createBlockData(Material.DIAMOND_BLOCK))
        }
        return selectionType

    }

    fun removeSelection(player: Player) {
        if (primary != null && secondary != null) {
            player.sendBlockChange(primary!!, Bukkit.createBlockData(primary!!.block.type))
            player.sendBlockChange(secondary!!, Bukkit.createBlockData(secondary!!.block.type))
            primary = null
            secondary = null
        }
    }

    fun bothPointsSelected(): Boolean {
        return primary != null && secondary != null
    }

    enum class SelectionType {
        PRIMARY, SECONDARY;
    }

    enum class ClickType {
        LEFT, RIGHT
    }

}