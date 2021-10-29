/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.text.DecimalFormat

class SurrenderCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "surrender") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                val enemyCivilization = CivManager.getByName(args[0])
                checkNotNull(
                    enemyCivilization,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
                )
                if (!relationships.warring.contains(enemyCivilization)) returnTell(Localization.Warnings.NOT_WARRING)
                fun run() {
                    if (hasCooldown(this, CooldownTask.CooldownType.END_WAR)) {
                        checkBoolean(
                            bank.balance - Settings.SURRENDER_COST < 0,
                            Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                                "{cost}",
                                Settings.SURRENDER_COST.toString().format(DecimalFormat.getCurrencyInstance())
                            )
                        )
                        bank.removeBalance(Settings.SURRENDER_COST)
                        enemyCivilization?.bank?.addBalance(Settings.SURRENDER_COST)
                    }
                    relationships.enemies.remove(enemyCivilization)
                    enemyCivilization?.addPower(Settings.POWER_WAR_WIN)
                    tellSuccess(Localization.Notifications.SURRENDERED.replace("{civ}", enemyCivilization?.name!!))
                }
                ConfirmMenu(
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
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}