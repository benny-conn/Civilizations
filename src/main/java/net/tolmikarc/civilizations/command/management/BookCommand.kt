/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Material
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager

class BookCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "book") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, "You must have a Civilization to set a Civilization's Book.")
            it.civilization?.apply {
                val param = args[0]
                if (param.equals("set", ignoreCase = true)) {
                    val book = player.inventory.itemInMainHand
                    checkBoolean(book.type == Material.WRITTEN_BOOK, "You must be holding a Book to use this command.")
                    checkBoolean(
                        this.leader == it,
                        "You must be the leader of the Civilization to set the Book"
                    )
                    this.book = book
                    tellSuccess("${Settings.SECONDARY_COLOR}Successfully set your Civilization's Book to the Book in your hand")
                } else if (param.equals("get", ignoreCase = true)) {
                    checkNotNull(this.book, "Your Civilization does not have a Book.")
                    this.book?.let { banner ->
                        checkBoolean(
                            HookManager.getBalance(player) >= 200,
                            "You need at least $200 to obtain this item."
                        )
                        HookManager.withdraw(player, 200.0)
                        tellSuccess("${Settings.SECONDARY_COLOR}Successfully obtained your Civilization's Banner")
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