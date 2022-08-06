/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.menu

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.menu.Menu
import org.mineacademy.fo.menu.button.Button
import org.mineacademy.fo.menu.model.ItemCreator
import org.mineacademy.fo.remain.CompMaterial

class ConfirmMenu(title: String, ci: String, result: () -> Unit) : Menu() {
    private val confirmButton: Button
    private val cancelButton: Button
    private val confirmInfo: String
    override fun getItemAt(slot: Int): ItemStack? {
        if (slot == 9 + 2) return confirmButton.item
        return if (slot == 9 + 6) cancelButton.item else null
    }

    override fun getInfo(): Array<String> {
        return arrayOf(confirmInfo)
    }

    init {
        size = 9 * 3
        confirmInfo = ci
        setTitle(title)
        confirmButton = object : Button() {
            override fun onClickedInMenu(player: Player, menu: Menu, clickType: ClickType) {
                result()
                player.closeInventory()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(CompMaterial.EMERALD_BLOCK)
                    .name("&a" + io.bennyc.civilizations.settings.Localization.CONFIRM.capitalize())
                    .make()
            }
        }
        cancelButton = object : Button() {
            override fun onClickedInMenu(player: Player, menu: Menu, clickType: ClickType) {
                player.closeInventory()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(CompMaterial.REDSTONE_BLOCK)
                    .name(io.bennyc.civilizations.settings.Localization.CANCEL.capitalize())
                    .make()
            }
        }
    }
}