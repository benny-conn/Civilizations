/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.event.civ.ClaimEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.Colony
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil.calculateFormulaForCiv
import net.tolmikarc.civilizations.util.ClaimUtil.distanceFromNearestClaim
import net.tolmikarc.civilizations.util.ClaimUtil.getRegionFromLocation
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationConnected
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInACiv
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInRegion
import net.tolmikarc.civilizations.util.ClaimUtil.isRegionConnected
import net.tolmikarc.civilizations.util.ClaimUtil.regionsInSelection
import net.tolmikarc.civilizations.util.MathUtil.areaBetweenTwoPoints
import org.bukkit.scheduler.BukkitRunnable
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.region.Region
import org.mineacademy.fo.remain.CompParticle
import java.util.*
import kotlin.math.abs

class ClaimCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "claim") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, "You do not have a civilization")
            it.civilization?.apply {
                if (args.isNotEmpty()) {
                    when (args[0].toLowerCase()) {
                        "visualize" -> {
                            if (args.size > 1 && !args[1].equals("here", true))
                                if (CivManager.getByName(args[1]) != null)
                                    visualize(it, CivManager.getByName(args[1])!!)
                                else
                                    returnInvalidArgs()
                            else
                                visualize(it, this)
                        }
                        "colony" -> {
                            claim(this, it, true)
                        }
                        "?" -> {
                            // TODO add info for claiming
                        }
                    }
                } else
                    claim(this, it, false)
            }
        }
    }

    private fun visualize(civPlayer: CPlayer, civilization: Civ) {
        civPlayer.visualizing = !civPlayer.visualizing
        val visualizedRegions: MutableSet<Region> = HashSet()
        if (args.size > 1 && args[1].equals("here", ignoreCase = true)) {
            getRegionFromLocation(player.location)?.let { visualizedRegions.add(it) }

        } else visualizedRegions.addAll(civilization.claims)
        if (civPlayer.visualizing) {
            tell("${Settings.SECONDARY_COLOR}Beginning to visualize...")
        } else {
            tell("&cStopping visualization...")
        }
        for (region in visualizedRegions) {
            Common.runTimerAsync(20 * 4, object : BukkitRunnable() {
                override fun run() {
                    if (civPlayer.civilization == null) civPlayer.visualizing = false
                    for (location in region.boundingBox.filter { location -> location.y in player.location.y - 5..player.location.y + 5 }) {
                        if (!civPlayer.visualizing) break
                        if (isLocationConnected(location, civilization, region)) continue
                        CompParticle.END_ROD.spawnFor(player, location)
                    }
                    if (!civPlayer.visualizing) cancel()
                }
            })

        }
    }

    private fun claim(civilization: Civ, player: CPlayer, isColony: Boolean) {
        checkBoolean(!player.visualizing, "Stop visualizing before you claim more land.")
        checkBoolean(player.selection.bothPointsSelected(), "You must have both points selected to claim")
        val claim = Region(
            civilization.uuid.toString() + (if (!isColony) "CLAIM" else "COLONY-CLAIM") + civilization.totalClaimCount,
            player.selection.primary,
            player.selection.secondary
        )
        val totalArea = abs(areaBetweenTwoPoints(claim.primary, claim.secondary))
        val isPointInOtherRegion =
            isLocationInACiv(player.selection.primary!!) || isLocationInACiv(player.selection.secondary!!)
        checkBoolean(
            !isPointInOtherRegion,
            "You may not claim a portion of another region. You can use /civ claim visualize to see your current claims."
        )
        val maxClaims = calculateFormulaForCiv(Settings.MAX_CLAIMS_FORMULA, civilization)
        if (maxClaims.toInt() != -1) checkBoolean(
            civilization.totalClaimCount < maxClaims,
            "You cannot have more than $maxClaims claims"
        )
        if (Settings.MAX_BLOCKS_COUNT != -1) checkBoolean(
            civilization.totalBlocksCount + totalArea < Settings.MAX_BLOCKS_COUNT,
            "You cannot have more than " + Settings.MAX_BLOCKS_COUNT + " blocks claimed"
        )
        if (Settings.MAX_CLAIM_SIZE != -1) checkBoolean(
            totalArea < Settings.MAX_CLAIM_SIZE,
            "You cannot claim more than an area of " + Settings.MAX_CLAIM_SIZE + " at once"
        )
        if (Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM != -1) checkBoolean(
            distanceFromNearestClaim(claim.center) > Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM,
            "You cannot claim so close to another Civilization's home"
        )
        val cost = calculateFormulaForCiv(Settings.COST_FORMULA, civilization, claim).toDouble()
        if (civilization.totalClaimCount > 0 && !isColony) checkBoolean(
            isRegionConnected(claim, civilization),
            "Claim must be connected to existing claim."
        )
        checkBoolean(
            regionsInSelection(claim).isEmpty(),
            "You cannot claim with another claimed region inside of your selection."
        )
        checkBoolean(
            HookManager.getBalance(getPlayer()) - cost > 0,
            "You do not have enough money to claim this amount of land."
        )
        checkBoolean(
            isLocationInRegion(getPlayer().location, claim),
            "You must be standing in your new claim to claim it."
        )
        if (civilization.totalClaimCount == 0)
            checkBoolean(
                !getPlayer().location.subtract(0.0, 1.0, 0.0).block.type.isAir,
                "You be standing on solid ground to claim land"
            )
        if (isColony) {
            checkBoolean(
                civilization.totalClaimCount > 0,
                "You must have at least one regular claim before creating a colony."
            )
            checkBoolean(
                distanceFromNearestClaim(claim.center, civilization) > Settings.COLONY_MIN_DISTANCE_FROM_NEAREST_CLAIM,
                "You cannot claim a colony so close to your civilization"
            )
            val maxColonies = calculateFormulaForCiv(Settings.MAX_COLONIES_FORMULA, civilization)
            if (maxColonies.toInt() != -1) checkBoolean(
                civilization.colonyCount < maxColonies,
                "You cannot have more than $maxColonies colonies."
            )
            val colony = Colony(civilization, civilization.idNumber, getPlayer().location)
            civilization.addColony(colony)
            tellSuccess("${Settings.SECONDARY_COLOR}Claimed colony with id ${Settings.PRIMARY_COLOR}" + civilization.idNumber)
        } else {
            tellSuccess("${Settings.SECONDARY_COLOR}Claimed region with id ${Settings.PRIMARY_COLOR}" + civilization.idNumber)
        }
        if (civilization.totalClaimCount == 0) civilization.home = getPlayer().location
        HookManager.withdraw(getPlayer(), cost)
        player.selection.removeSelection(getPlayer())
        civilization.addClaim(claim)
        Common.callEvent(ClaimEvent(civilization, claim, getPlayer()))
    }

    override fun tabComplete(): List<String> {
        if (args.size == 1) return listOf("colony", "visualize")
        return if (args.size == 2) listOf("here") else super.tabComplete()
    }

    init {
        usage = "[colony | visualize]"
        setDescription("Claim a new piece of land for your Civilization or visualize current ones.")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}