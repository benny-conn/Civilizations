/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil
import net.tolmikarc.civilizations.util.MathUtil.doubleToMoney
import net.tolmikarc.civilizations.util.MathUtil.isDouble
import org.bukkit.Material
import org.bukkit.Tag
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ASetCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "set") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
            when (args[1].toLowerCase()) {
                "money" -> {
                    checkArgs(3, "Please specify an amount to apply")
                    checkBoolean(isDouble(args[2]), "Please specify a valid number.")
                    bank.balance = doubleToMoney(args[2].toDouble())
                    tellSuccess("{1}Successfully set {2}$name's {1}balance to {2}${bank.balance}")
                }
                "description" -> {
                    checkArgs(3, "Please specify a description to apply")
                    description = args[2]
                    tellSuccess("{1}Set {2}$name's {1}description to {2}$description")
                }
                "name" -> {
                    checkArgs(3, "Please specify a name to apply")
                    name = args[2]
                    tellSuccess("{1}Renamed Civ to {2}$name")
                }
                "taxes" -> {
                    val amount = findNumber(
                        2,
                        0,
                        Settings.MAX_TAXES.toInt(),
                        "Please specify a valid tax amount between 0 and ${Settings.MAX_TAXES.toInt()}"
                    )
                    bank.taxes = amount.toDouble()
                    tellSuccess("{1}Set taxes to {2}${bank.taxes}")
                }
                "banner" -> {
                    checkConsole()
                    val banner = player.inventory.itemInMainHand
                    checkBoolean(Tag.BANNERS.isTagged(banner.type), "You must be holding a banner to use this command.")
                    this.banner = banner
                    tellSuccess("{1}Successfully set $name's Banner to the Banner in your hand")
                }
                "book" -> {
                    checkConsole()
                    val book = player.inventory.itemInMainHand
                    checkBoolean(book.type == Material.WRITTEN_BOOK, "You must be holding a Book to use this command.")
                    this.book = book
                    tellSuccess("{1}Successfully set $name's Book to the Book in your hand")
                }
                "home" -> {
                    checkConsole()
                    checkBoolean(
                        ClaimUtil.isLocationInCiv(player.location, this),
                        "You must be in your Civilization to set the home."
                    )
                    home = player.location
                    tellSuccess("{1}Set the $name's home location")
                }

            }
        }
    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 2) listOf("money", "name", "home", "taxes", "banner", "book", "description")
        else
            super.tabComplete()
    }

    init {
        setDescription("Set settings for a Civ")
        usage = "<civ> <setting> [value]"
        minArguments = 2
    }
}