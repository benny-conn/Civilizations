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
import net.tolmikarc.civilizations.settings.Localization
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
        val plot: Plot? = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, Localization.Warnings.Claim.NO_PLOT)
        checkBoolean(
            PermissionChecker.canManagePlot(civilization, plot!!, civPlayer),
            Localization.Warnings.CANNOT_MANAGE_PLOT
        )
        plot.apply {
            if (args.size < 2) returnInvalidArgs()
            val newPlayer = findPlayer(
                args[0],
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
            )
            val civNewPlayer = PlayerManager.fromBukkitPlayer(newPlayer)
            addMember(civNewPlayer)
            tellSuccess("{1}Added {2}" + newPlayer.displayName + "{1} to your plot")
        }
    }

    fun setPlotForSale(civPlayer: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, Localization.Warnings.Claim.NO_PLOT)
        plot?.apply {
            checkBoolean(
                PermissionChecker.canManagePlot(civilization, this, civPlayer),
                Localization.Warnings.CANNOT_MANAGE_PLOT
            )
            if (args.size > 1) {
                checkBoolean(
                    MathUtil.isDouble(args[1]),
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
                )
                price = MathUtil.doubleToMoney(args[1].toDouble())
            }
            forSale = true
            tellSuccess("{1}Successfully put this plot up for sale at the price {2}$price")
        }
    }

    fun claimPlot(civPlayer: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, Localization.Warnings.Claim.NO_PLOT)
        plot?.apply {
            checkBoolean(forSale, Localization.Warnings.Claim.NOT_FOR_SALE)
            checkBoolean(
                HookManager.getBalance(player) - price > 0,
                Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace("{cost}", price.toString())
            )
            HookManager.withdraw(player, price)
            owner = civPlayer
            price = 0.0
            forSale = false
            addMember(civPlayer)
            tellSuccess("{1}Successfully claimed this plot.")
        }
    }

    fun definePlot(civPlayer: CPlayer, civilization: Civ) {
        checkBoolean(PermissionChecker.canManageCiv(civPlayer, civilization), Localization.Warnings.CANNOT_MANAGE_CIV)
        checkBoolean(civPlayer.selection.bothPointsSelected(), Localization.Warnings.Claim.INCOMPLETE_SELECTION)
        val maxPlots = CivUtil.calculateFormulaForCiv(Settings.MAX_PLOTS_FORMULA, civilization)
        checkBoolean(
            civilization.claims.plotCount.toDouble() < maxPlots,
            Localization.Warnings.Claim.MAX_PLOTS.replace("{max}", maxPlots.toString())
        )
        val plotRegion =
            Claim(civilization.claims.plotCount, civPlayer.selection.primary!!, civPlayer.selection.secondary!!)
        checkBoolean(
            ClaimUtil.isLocationInCiv(plotRegion.primary, civilization) && ClaimUtil.isLocationInCiv(
                plotRegion.secondary,
                civilization
            ), Localization.Warnings.Claim.BEYOND_BORDERS
        )
        checkBoolean(
            ClaimUtil.plotsInSelection(plotRegion)
                .isEmpty() && ClaimUtil.getPlotFromLocation(plotRegion.primary) == null && ClaimUtil.getPlotFromLocation(
                plotRegion.secondary
            ) == null, Localization.Warnings.Claim.PLOT_OVERLAP
        )
        Plot(civilization, civilization.claims.idNumber, plotRegion, civilization.leader!!).apply {
            civilization.claims.addPlot(
                this
            )
        }
            .also { tellSuccess("{1}Successfully defined new plot with id {2}" + civilization.claims.plotCount) }
    }

    fun deletePlot(civPlayer: CPlayer, civilization: Civ) {
        checkBoolean(PermissionChecker.canManageCiv(civPlayer, civilization), Localization.Warnings.CANNOT_MANAGE_CIV)
        val plot = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, Localization.Warnings.Claim.NO_PLOT)
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
            checkNotNull(plotHere, Localization.Warnings.Claim.NO_PLOT)
            visualizedRegions.add(plotHere!!.region)
            if (!civPlayer.visualizing) civPlayer.visualizing = true
        } else civilization.claims.plots.forEach { claimedPlot -> visualizedRegions.add(claimedPlot.region) }
        if (civPlayer.visualizing) {
            tell("{1}Beginning to visualize...")
        } else {
            tell("{3}Stopping visualization...")
        }
        for (region in visualizedRegions) {
            Common.runTimerAsync(20 * Settings.PARTICLE_FREQUENCY, object : BukkitRunnable() {
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
        // overridden in all cases {default command}
    }

}