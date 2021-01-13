/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class TaxesCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "taxes") {
    override fun onCommand() {
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, "You must have a civ to use this command")
        civ?.apply {
            checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), "You cannot manage this Civilization")
            val amount = findNumber(0, "Please specify a valid number").toDouble()
            if (amount > Settings.MAX_TAXES)
                returnTell("The maximum tax amount is ${Settings.MAX_TAXES}")
            bank.taxes = amount
            tellSuccess("Successfully set the tax amount to ${Settings.CURRENCY_SYMBOL}$amount")
        }
    }

    init {
        minArguments = 1
        usage = "<${Settings.CURRENCY_SYMBOL}>"
        if (Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}