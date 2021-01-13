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

    fun addMemberToPlot(civPlayer: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(getPlayer().location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        checkBoolean(
            PermissionChecker.canManagePlot(civilization, plot!!, civPlayer),
            "You must own this plot to add members to it"
        )
        plot.apply {
            checkBoolean(args.size > 1, "Please specify who you would like to add to your plot.")
            val newPlayer = findPlayer(args[0], "Specify a valid and online player")
            val civNewPlayer = PlayerManager.fromBukkitPlayer(newPlayer)
            addMember(civNewPlayer)
            tellSuccess("{1}Added {2}" + newPlayer.displayName + "{1} to your plot")
        }
    }

    fun setPlotForSale(civPlayer: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        plot?.apply {
            checkBoolean(
                PermissionChecker.canManagePlot(civilization, this, civPlayer),
                "You must own this plot to set it for sale"
            )
            if (args.size > 1) {
                checkBoolean(MathUtil.isDouble(args[1]), "Please specify a valid number")
                price = MathUtil.doubleToMoney(args[1].toDouble())
            }
            forSale = true
            tellSuccess("{1}Successfully put this plot up for sale at the price {2}" + price)
        }
    }

    fun claimPlot(civPlayer: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        plot?.apply {
            checkBoolean(forSale, "This plot is not for sale")
            checkBoolean(HookManager.getBalance(player) - price > 0, "You cannot afford this claim")
            HookManager.withdraw(player, price)
            owner = civPlayer
            price = 0.0
            forSale = false
            addMember(civPlayer)
            tellSuccess("{1}Successfully claimed this plot.")
        }
    }

    fun definePlot(civPlayer: CPlayer, civilization: Civ) {
        checkBoolean(PermissionChecker.canManageCiv(civPlayer, civilization), "You cannot manage this Civilization")
        checkBoolean(civPlayer.selection.bothPointsSelected(), "You must have both points selected to claim")
        val maxPlots = CivUtil.calculateFormulaForCiv(Settings.MAX_PLOTS_FORMULA, civilization)
        checkBoolean(
            civilization.claims.plotCount.toDouble() < maxPlots,
            "You cannot define more than $maxPlots total plots."
        )
        val plotRegion =
            Claim(civilization.claims.plotCount, civPlayer.selection.primary!!, civPlayer.selection.secondary!!)
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
            .also { tellSuccess("{1}Successfully defined new plot with id {2}" + civilization.claims.plotCount) }
    }

    fun deletePlot(civPlayer: CPlayer, civilization: Civ) {
        checkBoolean(PermissionChecker.canManageCiv(civPlayer, civilization), "You cannot manage this Civilization")
        val plot = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, "You are not standing in a plot")
        plot?.apply {
            civilization.claims.removePlot(this)
            tellSuccess("Successfully deleted the plot at your location.")
        }
    }

    fun visualize(civPlayer: CPlayer, civilization: Civ) {
        civPlayer.visualizing = !civPlayer.visualizing
        val visualizedRegions: MutableSet<Claim> = HashSet()
        if (args.size > 1) {
            checkBoolean(args[1].equals("here", ignoreCase = true), usageMessage)
            val plotHere = ClaimUtil.getPlotFromLocation(player.location, civilization)
            checkNotNull(plotHere, "There is no plot at your location")
            visualizedRegions.add(plotHere!!.region)
            tell("Visualizing plot with ID " + plotHere.id)
            if (!civPlayer.visualizing) civPlayer.visualizing = true
        } else civilization.claims.plots.forEach { claimedPlot -> visualizedRegions.add(claimedPlot.region) }
        if (civPlayer.visualizing) {
            tell("{1}Beginning to visualize...")
        } else {
            tell("{3}Stopping visualization...")
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