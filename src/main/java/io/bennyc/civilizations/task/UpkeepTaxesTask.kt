/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.task

import io.bennyc.civilizations.manager.CivManager
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import org.mineacademy.fo.Common
import org.mineacademy.fo.model.HookManager
import java.util.*

class UpkeepTaxesTask : BukkitRunnable() {
    override fun run() {
        Common.log("Collecting Upkeep and Taxes")
        if (Calendar.HOUR_OF_DAY % 12 == 0)
            io.bennyc.civilizations.manager.CivManager.all.forEach { civ ->
                civ.citizens.forEach { player ->
                    if (HookManager.getBalance(Bukkit.getPlayer(player.uuid)) - civ.bank.taxes < 0)
                        civ.removeCitizen(player)
                    else
                        HookManager.withdraw(Bukkit.getPlayer(player.uuid), civ.bank.taxes)
                }
                if (civ.bank.balance - civ.bank.upkeep < 0)
                    io.bennyc.civilizations.manager.CivManager.removeCiv(civ)
                else
                    civ.bank.removeBalance(civ.bank.upkeep)
            }
    }

}