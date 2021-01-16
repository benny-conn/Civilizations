/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Material
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager

class BookCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "book") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, Localization.Warnings.NO_CIV)
            it.civilization?.apply {
                val param = args[0]
                if (param.equals("set", ignoreCase = true)) {
                    val book = player.inventory.itemInMainHand
                    checkBoolean(book.type == Material.WRITTEN_BOOK, Localization.Warnings.INVALID_HAND_ITEM)
                    checkBoolean(
                        PermissionChecker.canManageCiv(it, this),
                        Localization.Warnings.LEADER
                    )
                    this.book = book
                    tellSuccess("{2}Successfully set your Civilization's Book to the Book in your hand")
                } else if (param.equals("get", ignoreCase = true)) {
                    checkNotNull(this.book, Localization.Warnings.NO_BOOK)
                    this.book?.let { banner ->
                        val cost = 200
                        checkBoolean(
                            HookManager.getBalance(player) >= cost,
                            Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace("{cost}", cost.toString())
                        )
                        HookManager.withdraw(player, 200.0)
                        tellSuccess("{2}Successfully obtained your Civilization's Book")
                        player.inventory.addItem(banner)
                    }
                }
            }
        }
    }

    init {
        setDescription("Set the guiding book of your Civilization to the book in your hand")
        minArguments = 1
        usage = "<set | get>"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}