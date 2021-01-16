/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.command.parents.PlotSubCommand
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup

class PlotCommand(parent: SimpleCommandGroup?) : PlotSubCommand(parent) {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                if (args.isNotEmpty())
                    when (args[0].toLowerCase()) {
                        "visualize" -> visualize(civPlayer, this)
                        "define" -> definePlot(civPlayer, this)
                        "claim" -> claimPlot(civPlayer, this)
                        "delete" -> deletePlot(civPlayer, this)
                        "forsale" -> setPlotForSale(civPlayer, this)
                        "add" -> addMemberToPlot(civPlayer, this)
                    }
                PlayerManager.queueForSaving(civPlayer)
            }
        }
    }

    override fun tabComplete(): List<String>? {
        if (args.size == 1) return listOf("define", "claim", "visualize", "forsale", "add")
        return super.tabComplete()
    }

    init {
        minArguments = 1
        usage = "<define | claim | add | forsale | visualize> [...]"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}