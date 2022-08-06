/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.command.parent.PlotSubCommand
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup

class APlotCommand(parent: SimpleCommandGroup?) : io.bennyc.civilizations.command.parent.PlotSubCommand(parent) {
    override fun onCommand() {
        checkConsole()
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            if (args.isNotEmpty())
                when (args[1].toLowerCase()) {
                    "visualize" -> visualize(civPlayer, this)
                    "define"    -> definePlot(civPlayer, this)
                    "claim"     -> claimPlot(civPlayer, this)
                    "delete"    -> deletePlot(civPlayer, this)
                    "forsale"   -> setPlotForSale(civPlayer, this)
                    "add"       -> addMemberToPlot(civPlayer, this)
                }
            io.bennyc.civilizations.manager.PlayerManager.saveAsync(civPlayer)

        }
    }

    override fun tabComplete(): List<String>? {
        if (args.size == 2) return listOf("define", "claim", "visualize", "forsale", "add")
        return super.tabComplete()
    }

    init {
        minArguments = 2
        usage = "<civ> <define | claim | add | forsale | visualize> [...]"
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}