/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.parents

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.model.impl.Plot
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.ClaimUtil
import net.tolmikarc.civilizations.util.MathUtil
import org.bukkit.Location
import org.bukkit.scheduler.BukkitRunnable
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import java.util.*

open class PlotSubCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "plot") {

    fun addMemberToPlot(player: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(getPlayer().location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        checkBoolean(
            PermissionChecker.canManagePlot(civilization, plot!!, player),
            "You must own this plot to add members to it"
        )
        plot.apply {
            checkBoolean(args.size > 1, "Please specify who you would like to add to your plot.")
            val newPlayer = findPlayer(args[0], "Specify a valid and online player")
            val civNewPlayer = PlayerManager.fromBukkitPlayer(newPlayer)
            addMember(civNewPlayer)
            tellSuccess("${Settings.PRIMARY_COLOR}Added ${Settings.SECONDARY_COLOR}" + newPlayer.displayName + "${Settings.PRIMARY_COLOR} to your plot")
        }
    }

    fun setPlotForSale(player: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(getPlayer().location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        plot?.apply {
            checkBoolean(
                PermissionChecker.canManagePlot(civilization, this, player),
                "You must own this plot to set it for sale"
            )
            if (args.size > 1) {
                checkBoolean(MathUtil.isDouble(args[1]), "Please specify a valid number")
                price = MathUtil.doubleToMoney(args[1].toDouble())
            }
            forSale = true
            tellSuccess("${Settings.PRIMARY_COLOR}Successfully put this plot up for sale at the price ${Settings.SECONDARY_COLOR}" + price)
        }
    }

    fun claimPlot(player: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(getPlayer().location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        plot?.apply {
            checkBoolean(forSale, "This plot is not for sale")
            checkBoolean(HookManager.getBalance(getPlayer()) - price > 0, "You cannot afford this claim")
            HookManager.withdraw(getPlayer(), price)
            owner = player
            price = 0.0
            forSale = false
            addMember(player)
            tellSuccess("${Settings.PRIMARY_COLOR}Successfully claimed this plot.")
        }
    }

    fun definePlot(player: CPlayer, civilization: Civ) {
        checkBoolean(PermissionChecker.canManageCiv(player, civilization), "You cannot manage this Civilization")
        checkBoolean(player.selection.bothPointsSelected(), "You must have both points selected to claim")
        val maxPlots = CivUtil.calculateFormulaForCiv(Settings.MAX_PLOTS_FORMULA, civilization)
        checkBoolean(
            civilization.claims.plotCount.toDouble() < maxPlots,
            "You cannot define more than $maxPlots total plots."
        )
        val plotRegion = Claim(civilization.claims.plotCount, player.selection.primary!!, player.selection.secondary!!)
        checkBoolean(
            ClaimUtil.isLocationInCiv(plotRegion.primary, civilization) && ClaimUtil.isLocationInCiv(
                plotRegion.secondary,
                civilization
            ), "You cannot claim a new plot extending beyond your town"
        )
        checkBoolean(
            ClaimUtil.plotsInSelection(plotRegion)
                .isEmpty() && ClaimUtil.getPlotFromLocation(plotRegion.primary) == null && ClaimUtil.getPlotFromLocation(
                plotRegion.secondary
            ) == null, "You cannot claim a new plot with overlapping plots already claimed"
        )
        Plot(civilization, civilization.claims.idNumber, plotRegion, civilization.leader!!).apply {
            civilization.claims.addPlot(
                this
            )
        }
            .also { tellSuccess("${Settings.PRIMARY_COLOR}Successfully defined new plot with id ${Settings.SECONDARY_COLOR}" + civilization.claims.plotCount) }
    }

    fun visualize(civPlayer: CPlayer, civilization: Civ) {
        civPlayer.visualizing = !civPlayer.visualizing
        val visualizedRegions: MutableSet<Claim> = HashSet()
        if (args.size > 1) {
            checkBoolean(args[1].equals("here", ignoreCase = true), usageMessage)
            val plotHere = ClaimUtil.getPlotFromLocation(getPlayer().location, civilization)
            checkNotNull(plotHere, "There is no plot at your location")
            visualizedRegions.add(plotHere!!.region)
            tell("Visualizing plot with ID " + plotHere.id)
            if (!civPlayer.visualizing) civPlayer.visualizing = true
        } else civilization.claims.plots.forEach { claimedPlot -> visualizedRegions.add(claimedPlot.region) }
        if (civPlayer.visualizing) {
            tell("${Settings.SECONDARY_COLOR}Beginning to visualize...")
        } else {
            tell("&cStopping visualization...")
        }
        for (region in visualizedRegions) {
            Common.runTimerAsync(20 * 4, object : BukkitRunnable() {
                override fun run() {
                    for (location in region.boundingBox.filter { it.y in (player.location.y - 6)..(player.location.y + 6) }) {
                        if (isLocationConnected(location, civilization, region)) continue
                        Settings.PLOT_PARTICLE.spawnFor(player, location)
                    }
                    if (!civPlayer.visualizing) cancel()
                }
            })
        }
    }

    fun isLocationConnected(location: Location, civilization: Civ, excludedRegion: Claim): Boolean {
        for (plot in civilization.claims.plots) {
            if (plot.region == excludedRegion) continue
            if (plot.region.boundingBox.contains(location)) return true
        }
        return false
    }

    override fun onCommand() {
        // overridden in all cases
    }

}