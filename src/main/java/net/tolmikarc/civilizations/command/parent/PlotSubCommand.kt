/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.parent

import kotlinx.coroutines.delay
import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Plot
import net.tolmikarc.civilizations.model.impl.Region
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.ClaimUtil
import net.tolmikarc.civilizations.util.MathUtil
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit

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
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
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
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
        }
    }

    fun claimPlot(civPlayer: CPlayer, civilization: Civ) {
        val plot: Plot? = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, Localization.Warnings.Claim.NO_PLOT)
        plot?.apply {
            checkBoolean(forSale, Localization.Warnings.Claim.NOT_FOR_SALE)
            checkBoolean(
                HookManager.getBalance(player) - price > 0,
                Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace(
                    "{cost}",
                    price.toString().format(DecimalFormat.getCurrencyInstance())
                )
            )
            HookManager.withdraw(player, price)
            owner = civPlayer
            price = 0.0
            forSale = false
            addMember(civPlayer)
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
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
            Region(
                civilization.claims.plotCount,
                civPlayer.selection.primary!!,
                civPlayer.selection.secondary!!
            )
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
            .also { tellSuccess(Localization.Notifications.SUCCESS_COMMAND) }
    }

    fun deletePlot(civPlayer: CPlayer, civilization: Civ) {
        checkBoolean(PermissionChecker.canManageCiv(civPlayer, civilization), Localization.Warnings.CANNOT_MANAGE_CIV)
        val plot = ClaimUtil.getPlotFromLocation(player.location, civilization)
        checkNotNull(plot, Localization.Warnings.Claim.NO_PLOT)
        plot?.apply {
            civilization.claims.removePlot(this)
            tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
        }
    }

    fun visualize(civPlayer: CPlayer, civilization: Civ) {
        civPlayer.visualizing = !civPlayer.visualizing
        val visualizedRegions: MutableSet<Region> = HashSet()
        if (args.size > 1) {
            checkBoolean(args[1].equals("here", ignoreCase = true), usageMessage)
            val plotHere = ClaimUtil.getPlotFromLocation(player.location, civilization)
            checkNotNull(plotHere, Localization.Warnings.Claim.NO_PLOT)
            visualizedRegions.add(plotHere!!.region)
            if (!civPlayer.visualizing) civPlayer.visualizing = true
        } else civilization.claims.plots.forEach { claimedPlot -> visualizedRegions.add(claimedPlot.region) }
        if (civPlayer.visualizing) {
            tell(Localization.Notifications.VISUALIZE_START)
        } else {
            tell(Localization.Notifications.VISUALIZE_END)
        }
        AsyncEnvironment.run {
            while (civPlayer.visualizing) {
                for (region in visualizedRegions) {
                    for (loc in region.boundingBox.filter { player.location.distance(it) < 20 }) {
                        Settings.CLAIM_PARTICLE.spawnFor(player, loc)
                    }
                }
                delay(((1000 * Settings.PARTICLE_FREQUENCY) / visualizedRegions.size).toLong())
            }
        }
    }

    fun toggle(civPlayer: CPlayer, civilization: Civ) {
        checkArgs(2, "Please specify a value to toggle")
        val arg = args[1]
        val plot: Plot = ClaimUtil.getPlotFromLocation(player.location, civilization) ?: return
        plot.apply {
            checkBoolean(
                PermissionChecker.canManagePlot(this.civ, this, civPlayer),
                Localization.Warnings.CANNOT_MANAGE_CIV
            )
            when (arg) {
                "fire" -> toggleables.fire =
                    !toggleables.fire.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.fire.toString()
                            )
                        )
                    }
                "explosions" -> toggleables.explosion =
                    !toggleables.explosion.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.explosion.toString()
                            )
                        )
                    }
                "mobs" -> toggleables.mobs =
                    !toggleables.mobs.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.mobs.toString()
                            )
                        )
                    }
                "pvp" -> toggleables.pvp =
                    !toggleables.pvp.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.pvp.toString()
                            )
                        )
                        setCooldown(Settings.PVP_TOGGLE_COOLDOWN, TimeUnit.SECONDS)
                    }
                "public" -> toggleables.public =
                    !toggleables.public.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.public.toString()
                            )
                        )
                    }
                "inviteonly" -> toggleables.inviteOnly =
                    !toggleables.inviteOnly.also {
                        tellSuccess(
                            Localization.Notifications.SUCCESS_TOGGLE.replace(
                                "{value}",
                                toggleables.inviteOnly.toString()
                            )
                        )
                    }
                else -> returnInvalidArgs()
            }
        }
    }

    fun info(civilization: Civ) {
        val plot: Plot = ClaimUtil.getPlotFromLocation(player.location, civilization) ?: return
        val toggleables = plot.toggleables
        val membersNames: MutableList<String?> = ArrayList()
        for (player in civilization.citizens) {
            membersNames.add(player.playerName)
        }
        tellNoPrefix(
            "${Settings.PRIMARY_COLOR}============ ${Settings.SECONDARY_COLOR}" + civilization.name + "${Settings.PRIMARY_COLOR} ============",
            "${Settings.PRIMARY_COLOR}Owner: ${Settings.SECONDARY_COLOR}" + plot.owner,
            "${Settings.PRIMARY_COLOR}Members: ${Settings.SECONDARY_COLOR}" + Common.join(
                membersNames,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}============================",
            "${Settings.PRIMARY_COLOR}PVP: ${Settings.SECONDARY_COLOR}" + toggleables.pvp + " ${Settings.PRIMARY_COLOR}Mob Spawning: ${Settings.SECONDARY_COLOR}" + toggleables.mobs + " ${Settings.PRIMARY_COLOR}Explosions: ${Settings.SECONDARY_COLOR}" + toggleables.explosion + " ${Settings.PRIMARY_COLOR}Fire Spread: ${Settings.SECONDARY_COLOR}" + toggleables.fire + " ${Settings.PRIMARY_COLOR}Public: ${Settings.SECONDARY_COLOR}" + toggleables.public + " ${Settings.PRIMARY_COLOR}Invite Only: ${Settings.SECONDARY_COLOR}" + toggleables.inviteOnly,
            "${Settings.PRIMARY_COLOR}============================"
        )
    }

    override fun onCommand() {
        // overridden in all cases {default command}
    }

}