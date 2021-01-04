/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.model.Plot
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil.calculateFormulaForCiv
import net.tolmikarc.civilizations.util.ClaimUtil.getPlotFromLocation
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import net.tolmikarc.civilizations.util.ClaimUtil.plotsInSelection
import net.tolmikarc.civilizations.util.MathUtil.doubleToMoney
import net.tolmikarc.civilizations.util.MathUtil.isDouble
import net.tolmikarc.civilizations.util.PermissionUtil.canManagePlot
import org.bukkit.Location
import org.bukkit.scheduler.BukkitRunnable
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.region.Region
import org.mineacademy.fo.remain.CompParticle
import java.util.*

class PlotCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "plot") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a civilization")
            civPlayer.civilization?.apply {
                if (args.isNotEmpty())
                    when (args[0].toLowerCase()) {
                        "visualize" -> visualize(civPlayer, this)
                        "define" -> definePlot(civPlayer, this)
                        "perms" -> adjustPermissions(civPlayer, this)
                        "permissions" -> adjustPermissions(civPlayer, this)
                        "claim" -> claimPlot(civPlayer, this)
                        "forsale" -> setPlotForSale(civPlayer, this)
                        "add" -> addMemberToPlot(civPlayer, this)
                    }
                queueForSaving()
            }
        }
    }

    private fun addMemberToPlot(player: CivPlayer, civilization: Civilization) {
        val plot: Plot? = getPlotFromLocation(getPlayer().location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        checkBoolean(canManagePlot(civilization, plot!!, player), "You must own this plot to add members to it")
        plot.apply {
            checkBoolean(args.size > 1, "Please specify who you would like to add to your plot.")
            val newPlayer = findPlayer(args[0], "Specify a valid and online player")
            val civNewPlayer = CivPlayer.fromBukkitPlayer(newPlayer)
            addMember(civNewPlayer!!)
            tellSuccess("${Settings.PRIMARY_COLOR}Added ${Settings.SECONDARY_COLOR}" + newPlayer.displayName + "${Settings.PRIMARY_COLOR} to your plot")
        }
    }

    private fun setPlotForSale(player: CivPlayer, civilization: Civilization) {
        val plot: Plot? = getPlotFromLocation(getPlayer().location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        plot?.apply {
            checkBoolean(canManagePlot(civilization, this, player), "You must own this plot to set it for sale")
            if (args.size > 1) {
                checkBoolean(isDouble(args[1]), "Please specify a valid number")
                price = doubleToMoney(args[1].toDouble())
            }
            forSale = true
            tellSuccess("${Settings.PRIMARY_COLOR}Successfully put this plot up for sale at the price ${Settings.SECONDARY_COLOR}" + price)
        }
    }

    private fun claimPlot(player: CivPlayer, civilization: Civilization) {
        val plot: Plot? = getPlotFromLocation(getPlayer().location, civilization)
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

    private fun adjustPermissions(civPlayer: CivPlayer, civilization: Civilization) {
        val plot: Plot? = getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, "There is no plot at your location")
        plot?.apply {
            checkBoolean(canManagePlot(civilization, this, civPlayer), "You cannot adjust the perms of this plot")
            val permissions: ClaimPermissions = claimPermissions
            if (args.size == 2) {
                if (args[1].equals("?", ignoreCase = true)) {
                    tell(
                        "${Settings.PRIMARY_COLOR}Valid Groups: ${Settings.SECONDARY_COLOR}Outsider, Member, Ally, Official",
                        "${Settings.PRIMARY_COLOR}Valid Permissions: ${Settings.SECONDARY_COLOR}Build, Break, Switch, Interact",
                        "${Settings.PRIMARY_COLOR}Valid values: ${Settings.SECONDARY_COLOR}True, False"
                    )
                } else returnInvalidArgs()
            }
            if (args.size == 4) {
                checkBoolean(permissions.adjustPerm(args[0], args[1], args[2]), "<permission | ?> <group> <value>")
                tellSuccess("${Settings.PRIMARY_COLOR}Successfully updated Plot Permissions")
                return
            }
            returnInvalidArgs()
        }
    }

    private fun definePlot(player: CivPlayer, civilization: Civilization) {
        checkNotNull(player.vertex1, "You do not have both region points set")
        checkNotNull(player.vertex2, "You do not have both region points set")
        val maxPlots = calculateFormulaForCiv(Settings.MAX_PLOTS_FORMULA, civilization)
        checkBoolean(civilization.plotCount.toDouble() < maxPlots, "You cannot define more than $maxPlots total plots.")
        val plotRegion = Region(civilization.plotCount.toString(), player.vertex1, player.vertex2)
        checkBoolean(
            isLocationInCiv(plotRegion.primary, civilization) && isLocationInCiv(
                plotRegion.secondary,
                civilization
            ), "You cannot claim a new plot extending beyond your town"
        )
        checkBoolean(
            plotsInSelection(plotRegion).isEmpty() && getPlotFromLocation(plotRegion.primary) == null && getPlotFromLocation(
                plotRegion.secondary
            ) == null, "You cannot claim a new plot with overlapping plots already claimed"
        )
        Plot(civilization, civilization.idNumber, plotRegion).apply { civilization.addPlot(this) }
            .also { tellSuccess("${Settings.PRIMARY_COLOR}Successfully defined new plot with id ${Settings.SECONDARY_COLOR}" + civilization.plotCount) }
    }

    private fun visualize(player: CivPlayer, civilization: Civilization) {
        player.visualizing = !player.visualizing
        val visualizedRegions: MutableSet<Region> = HashSet()
        if (args.size > 1) {
            checkBoolean(args[1].equals("here", ignoreCase = true), usageMessage)
            val plotHere = getPlotFromLocation(getPlayer().location, civilization)
            checkNotNull(plotHere, "There is no plot at your location")
            visualizedRegions.add(plotHere!!.region)
            tell("Visualizing plot with ID " + plotHere.id)
            if (!player.visualizing) player.visualizing = true
        } else civilization.plots.forEach { claimedPlot -> visualizedRegions.add(claimedPlot.region) }
        if (player.visualizing) {
            tell("${Settings.SECONDARY_COLOR}Beginning to visualize...")
        } else {
            tell("&cStopping visualization...")
        }
        for (region in visualizedRegions) {
            Common.runTimerAsync(20 * 4, object : BukkitRunnable() {
                override fun run() {
                    for (location in region.boundingBox) {
                        if (location.blockY > getPlayer().location.blockY + 10 || location.blockY < getPlayer().location.blockY - 10) continue
                        if (isLocationConnected(location, civilization, region)) continue
                        CompParticle.TOTEM.spawnFor(getPlayer(), location)
                    }
                    if (!player.visualizing) cancel()
                }
            })
        }
    }

    fun isLocationConnected(location: Location, civilization: Civilization, excludedRegion: Region): Boolean {
        for (plot in civilization.plots) {
            if (plot.region == excludedRegion) continue
            if (plot.region.boundingBox.contains(location)) return true
        }
        return false
    }

    override fun tabComplete(): List<String> {
        if (args.size == 1) return listOf("define", "claim", "visualize", "forsale", "add", "perms")
        if (args.size == 2) if (args[0].equals("add", ignoreCase = true)) return super.tabComplete()

        if (args[0].equals("perms", ignoreCase = true))
            when (args.size) {
                3 -> return listOf("outsider", "member", "ally", "official")
                4 -> return listOf("break", "build", "switch", "interact")
                5 -> return listOf("true", "false")
            }
        return super.tabComplete()
    }

    init {
        minArguments = 1
        usage = "<define | perms | claim | add | forsale | visualize> [...]"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}