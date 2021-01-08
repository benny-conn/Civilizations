/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.MathUtil.doubleToMoney
import net.tolmikarc.civilizations.util.MathUtil.isDouble
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager

class WithdrawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "withdraw") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You must have a civilization to put money into")
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    "You must be the Civilization Leader or an Official to withdraw money"
                )
                checkBoolean(isDouble(args[0]), "Please type in a valid number")
                val amount = doubleToMoney(args[0].toDouble())
                checkBoolean(bank.balance - amount > 0, "Your Civilization does not have enough money to withdraw")
                HookManager.deposit(player, amount)
                removeBalance(amount)
                tellSuccess("${Settings.PRIMARY_COLOR}Withdrew ${Settings.SECONDARY_COLOR}" + amount + "${Settings.PRIMARY_COLOR} from your Civilization's Bank")
            }
        }
    }

    init {
        setDescription("Withdraw Money into your Civilization")
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}