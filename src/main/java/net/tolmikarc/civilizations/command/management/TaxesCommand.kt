/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class TaxesCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "taxes") {
    override fun onCommand() {
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, Localization.Warnings.NO_CIV)
        civ?.apply {
            checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
            val amount = findNumber(
                0,
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
            ).toDouble()
            if (amount > Settings.MAX_TAXES)
                returnTell(Localization.Warnings.MAXIMUM.replace("{max}", Settings.MAX_TAXES.toString()))
            bank.taxes = amount
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
        }
    }

    init {
        minArguments = 1
        usage = "<>"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}