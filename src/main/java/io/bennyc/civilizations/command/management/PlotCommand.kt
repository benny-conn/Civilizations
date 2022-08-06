/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.command.parent.PlotSubCommand
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup

class PlotCommand(parent: SimpleCommandGroup?) : io.bennyc.civilizations.command.parent.PlotSubCommand(parent) {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                if (args.isNotEmpty())
                    when (args[0].toLowerCase()) {
                        "visualize" -> visualize(civPlayer, this)
                        "define"    -> definePlot(civPlayer, this)
                        "claim"     -> claimPlot(civPlayer, this)
                        "delete"    -> deletePlot(civPlayer, this)
                        "forsale"   -> setPlotForSale(civPlayer, this)
                        "toggle"    -> toggle(civPlayer, this)
                        "add"       -> addMemberToPlot(civPlayer, this)
                        "info"      -> info(this)
                    }
                io.bennyc.civilizations.manager.PlayerManager.saveAsync(civPlayer)
            }
        }
    }

    override fun tabComplete(): List<String>? {
        if (args.size == 1) return listOf("define", "claim", "visualize", "forsale", "add", "info", "toggle")
        return super.tabComplete()
    }

    init {
        minArguments = 1
        usage = "<define | claim | add | forsale | visualize | info | toggle> [...]"
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}