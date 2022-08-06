/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker.canManageCiv
import io.bennyc.civilizations.util.MathUtil.doubleToMoney
import io.bennyc.civilizations.util.MathUtil.isDouble
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import java.text.DecimalFormat

class WithdrawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "withdraw") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                checkBoolean(
                    isDouble(args[0]),
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.NUMBER)
                )
                val amount = doubleToMoney(args[0].toDouble())
                checkBoolean(
                    bank.balance - amount > 0,
                    io.bennyc.civilizations.settings.Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                        "{cost}", amount.toString().format(
                            DecimalFormat.getCurrencyInstance()
                        )
                    )
                )
                HookManager.deposit(player, amount)
                bank.removeBalance(amount)
                tellSuccess(
                    io.bennyc.civilizations.settings.Localization.Notifications.WITHDREW.replace(
                        "{cost}",
                        amount.toString().format(DecimalFormat.getCurrencyInstance())
                    )
                )
            }
        }
    }

    init {
        setDescription("Withdraw Money into your Civilization")
        minArguments = 1
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}