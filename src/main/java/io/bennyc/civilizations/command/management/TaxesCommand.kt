/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class TaxesCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "taxes") {
    override fun onCommand() {
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
        civ?.apply {
            checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
            val amount = findNumber(
                0,
                io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.NUMBER)
            ).toDouble()
            if (amount > io.bennyc.civilizations.settings.Settings.MAX_TAXES)
                returnTell(io.bennyc.civilizations.settings.Localization.Warnings.MAXIMUM.replace("{max}", io.bennyc.civilizations.settings.Settings.MAX_TAXES.toString()))
            bank.taxes = amount
            tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
        }
    }

    init {
        minArguments = 1
        usage = "<>"
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}