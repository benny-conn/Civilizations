/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.menu

import net.tolmikarc.civilizations.settings.Localization
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.menu.Menu
import org.mineacademy.fo.menu.button.Button
import org.mineacademy.fo.menu.model.ItemCreator
import org.mineacademy.fo.remain.CompMaterial

class ConfirmMenu(title: String, info: String, result: () -> Unit) : Menu() {
    private val confirmButton: Button
    private val cancelButton: Button
    override fun getItemAt(slot: Int): ItemStack? {
        if (slot == 9 + 2) return confirmButton.item
        return if (slot == 9 + 6) cancelButton.item else null
    }

    init {
        size = 9 * 3
        setInfo(info)
        setTitle(title)
        confirmButton = object : Button() {
            override fun onClickedInMenu(player: Player, menu: Menu, clickType: ClickType) {
                result()
                player.closeInventory()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(CompMaterial.EMERALD_BLOCK).name("&a" + Localization.CONFIRM).build().make()
            }
        }
        cancelButton = object : Button() {
            override fun onClickedInMenu(player: Player, menu: Menu, clickType: ClickType) {
                player.closeInventory()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(CompMaterial.REDSTONE_BLOCK).name("{3}" + Localization.CANCEL).build().make()
            }
        }
    }
}