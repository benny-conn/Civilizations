/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil
import net.tolmikarc.civilizations.util.MathUtil.doubleToMoney
import net.tolmikarc.civilizations.util.MathUtil.isDouble
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ASetCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "set") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            when (args[1].toLowerCase()) {
                "money" -> {
                    checkArgs(3, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER))
                    checkBoolean(
                        isDouble(args[2]),
                        Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
                    )
                    bank.balance = doubleToMoney(args[2].toDouble())
                }
                "description" -> {
                    checkArgs(3, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "description"))
                    description = args[2]
                }
                "name" -> {
                    checkArgs(3, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "name"))
                    name = args[2]
                }
                "leader" -> {
                    val newLeader = PlayerManager.getByName(args[0])
                    fun run() {
                        checkNotNull(
                            newLeader,
                            Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
                        )
                        checkBoolean(citizens.contains(newLeader), Localization.Warnings.NOT_IN_CIV)
                        leader = newLeader
                        tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                    }
                    ConfirmMenu("&4&lSet New Leader?", "WARNING: Irreversible", ::run)
                }
                "taxes" -> {
                    val amount = findNumber(
                        2,
                        0,
                        Settings.MAX_TAXES.toInt(),
                        "Please specify a valid tax amount between 0 and ${Settings.MAX_TAXES.toInt()}"
                    )
                    bank.taxes = amount.toDouble()
                }
                "home" -> {
                    checkConsole()
                    checkBoolean(
                        ClaimUtil.isLocationInCiv(player.location, this),
                        Localization.Warnings.Claim.NO_CLAIM
                    )
                    home = player.location
                }
            }
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
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