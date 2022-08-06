/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.util.ClaimUtil
import io.bennyc.civilizations.util.MathUtil.doubleToMoney
import io.bennyc.civilizations.util.MathUtil.isDouble
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ASetCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "set") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            when (args[1].toLowerCase()) {
                "money" -> {
                    checkArgs(3, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.NUMBER))
                    checkBoolean(
                        isDouble(args[2]),
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.NUMBER)
                    )
                    bank.balance = doubleToMoney(args[2].toDouble())
                }
                "description" -> {
                    checkArgs(3, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "description"))
                    description = args[2]
                }
                "name" -> {
                    checkArgs(3, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "name"))
                    name = args[2]
                }
                "leader" -> {
                    val newLeader = io.bennyc.civilizations.manager.PlayerManager.getByName(args[0])
                    fun run() {
                        checkNotNull(
                            newLeader,
                            io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER)
                        )
                        checkBoolean(citizens.contains(newLeader), io.bennyc.civilizations.settings.Localization.Warnings.NOT_IN_CIV)
                        leader = newLeader
                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
                    }
                    io.bennyc.civilizations.menu.ConfirmMenu("&4&lSet New Leader?", "WARNING: Irreversible", ::run)
                }
                "taxes" -> {
                    val amount = findNumber(
                        2,
                        0,
                        io.bennyc.civilizations.settings.Settings.MAX_TAXES.toInt(),
                        "Please specify a valid tax amount between 0 and ${io.bennyc.civilizations.settings.Settings.MAX_TAXES.toInt()}"
                    )
                    bank.taxes = amount.toDouble()
                }
                "home" -> {
                    checkConsole()
                    checkBoolean(
                        ClaimUtil.isLocationInCiv(player.location, this),
                        io.bennyc.civilizations.settings.Localization.Warnings.Claim.NO_CLAIM
                    )
                    home = player.location
                }
            }
            tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
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