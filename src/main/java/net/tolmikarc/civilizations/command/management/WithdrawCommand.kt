/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.MathUtil.doubleToMoney
import net.tolmikarc.civilizations.util.MathUtil.isDouble
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import java.text.DecimalFormat

class WithdrawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "withdraw") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                checkBoolean(
                    isDouble(args[0]),
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
                )
                val amount = doubleToMoney(args[0].toDouble())
                checkBoolean(
                    bank.balance - amount > 0,
                    Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                        "{cost}", amount.toString().format(
                            DecimalFormat.getCurrencyInstance()
                        )
                    )
                )
                HookManager.deposit(player, amount)
                bank.removeBalance(amount)
                tellSuccess(
                    Localization.Notifications.WITHDREW.replace(
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
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}