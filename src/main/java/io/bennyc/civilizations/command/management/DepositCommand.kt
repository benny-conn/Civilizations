/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.util.MathUtil.doubleToMoney
import io.bennyc.civilizations.util.MathUtil.isDouble
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import java.text.DecimalFormat

class DepositCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "deposit") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            it.civilization?.apply {
                checkBoolean(
                    isDouble(args[0]),
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.NUMBER)
                )
                val amount = doubleToMoney(args[0].toDouble())
                checkBoolean(
                    HookManager.getBalance(player) - amount > 0,
                    io.bennyc.civilizations.settings.Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace(
                        "{cost}",
                        amount.toString().format(DecimalFormat.getCurrencyInstance())
                    )
                )
                HookManager.withdraw(player, amount)
                bank.addBalance(amount)
                tellSuccess(
                    io.bennyc.civilizations.settings.Localization.Notifications.DEPOSITED.replace(
                        "{cost}",
                        amount.toString().format(DecimalFormat.getCurrencyInstance())
                    )
                )
            }
        }
    }

    init {
        setDescription("Deposit Money into your Civilization")
        minArguments = 1
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}