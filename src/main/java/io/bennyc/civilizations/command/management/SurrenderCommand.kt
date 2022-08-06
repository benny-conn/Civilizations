/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.text.DecimalFormat

class SurrenderCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "surrender") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                val enemyCivilization = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
                checkNotNull(
                    enemyCivilization,
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION)
                )
                if (!relationships.warring.contains(enemyCivilization)) returnTell(io.bennyc.civilizations.settings.Localization.Warnings.NOT_WARRING)
                fun run() {
                    if (hasCooldown(this, CooldownTask.CooldownType.END_WAR)) {
                        checkBoolean(
                            bank.balance - io.bennyc.civilizations.settings.Settings.SURRENDER_COST < 0,
                            io.bennyc.civilizations.settings.Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                                "{cost}",
                                io.bennyc.civilizations.settings.Settings.SURRENDER_COST.toString().format(DecimalFormat.getCurrencyInstance())
                            )
                        )
                        bank.removeBalance(io.bennyc.civilizations.settings.Settings.SURRENDER_COST)
                        enemyCivilization?.bank?.addBalance(io.bennyc.civilizations.settings.Settings.SURRENDER_COST)
                    }
                    relationships.enemies.remove(enemyCivilization)
                    enemyCivilization?.addPower(io.bennyc.civilizations.settings.Settings.POWER_WAR_WIN)
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SURRENDERED.replace("{civ}", enemyCivilization?.name!!))
                }
                io.bennyc.civilizations.menu.ConfirmMenu(
                    "&4Surrender?",
                    "Give up the war with ${enemyCivilization!!.name}. If you end a war early you may have to pay.",
                    ::run
                ).displayTo(player)
            }
        }
    }

    init {
        minArguments = 1
        usage = "<enemy>"
        setDescription("Surrender a war with another Civilization")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}