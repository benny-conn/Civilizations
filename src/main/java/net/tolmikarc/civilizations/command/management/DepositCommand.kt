/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.MathUtil.doubleToMoney
import net.tolmikarc.civilizations.util.MathUtil.isDouble
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import java.text.DecimalFormat

class DepositCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "deposit") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, Localization.Warnings.NO_CIV)
            it.civilization?.apply {
                checkBoolean(
                    isDouble(args[0]),
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
                )
                val amount = doubleToMoney(args[0].toDouble())
                checkBoolean(
                    HookManager.getBalance(player) - amount > 0,
                    Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace(
                        "{cost}",
                        amount.toString().format(DecimalFormat.getCurrencyInstance())
                    )
                )
                HookManager.withdraw(player, amount)
                bank.addBalance(amount)
                tellSuccess(
                    Localization.Notifications.DEPOSITED.replace(
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
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}