/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.parents

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.event.ClaimEvent
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.model.impl.Colony
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.ClaimUtil
import net.tolmikarc.civilizations.util.MathUtil
import org.bukkit.scheduler.BukkitRunnable
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager
import java.util.*
import kotlin.math.abs

open class ClaimSubCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "claim") {
    override fun onCommand() {
    }

    fun visualize(civPlayer: CPlayer, civilization: Civ) {
        civPlayer.visualizing = !civPlayer.visualizing
        val visualizedRegions: MutableSet<Claim> = HashSet()
        if (args.size > 1 && args[1].equals("here", ignoreCase = true)) {
            ClaimUtil.getRegionFromLocation(player.location)?.let { visualizedRegions.add(it) }

        } else visualizedRegions.addAll(civilization.claims.claims)
        if (civPlayer.visualizing) {
            tell("{2}Beginning to visualize...")
        } else {
            tell("{3}Stopping visualization...")
        }
        for (region in visualizedRegions) {
            Common.runTimerAsync(20 * Settings.PARTICLE_FREQUENCY, object : BukkitRunnable() {
                override fun run() {
                    if (civPlayer.civilization == null) civPlayer.visualizing = false
                    for (location in region.boundingBox.filter { it.y in (player.location.y - 6)..(player.location.y + 6) }) {
                        if (!civPlayer.visualizing) break
                        if (ClaimUtil.isLocationConnected(location, civilization, region)) continue
                        Settings.CLAIM_PARTICLE.spawnFor(player, location)
                    }
                    if (!civPlayer.visualizing) cancel()
                }
            })

        }
    }

    fun claim(civilization: Civ, player: CPlayer, isColony: Boolean) {
        checkBoolean(PermissionChecker.canManageCiv(player, civilization), "You cannot manage this Civilization")
        checkBoolean(!player.visualizing, "Stop visualizing before you claim more land.")
        checkBoolean(player.selection.bothPointsSelected(), "You must have both points selected to claim")
        val claim = Claim(
            civilization.claims.totalClaimCount,
            player.selection.primary!!,
            player.selection.secondary!!
        )
        val totalArea = abs(MathUtil.areaBetweenTwoPoints(claim.primary, claim.secondary))
        val isPointInOtherRegion =
            ClaimUtil.isLocationInACiv(player.selection.primary!!) || ClaimUtil.isLocationInACiv(player.selection.secondary!!)
        checkBoolean(
            !isPointInOtherRegion,
            "You may not claim a portion of another region. You can use /civ claim visualize to see your current claims."
        )
        val maxClaims = CivUtil.calculateFormulaForCiv(Settings.MAX_CLAIMS_FORMULA, civilization)
        if (maxClaims.toInt() != -1) checkBoolean(
            civilization.claims.totalClaimCount < maxClaims,
            "You cannot have more than $maxClaims claims"
        )
        if (Settings.MAX_BLOCKS_COUNT != -1) checkBoolean(
            civilization.claims.totalBlocksCount + totalArea < Settings.MAX_BLOCKS_COUNT,
            "You cannot have more than " + Settings.MAX_BLOCKS_COUNT + " blocks claimed"
        )
        if (Settings.MAX_CLAIM_SIZE != -1) checkBoolean(
            totalArea < Settings.MAX_CLAIM_SIZE,
            "You cannot claim more than an area of " + Settings.MAX_CLAIM_SIZE + " at once"
        )
        if (Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM != -1) checkBoolean(
            ClaimUtil.distanceFromNearestClaim(claim.center) > Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM,
            "You cannot claim so close to another Civilization's home"
        )
        if (civilization.claims.totalClaimCount > 0 && !isColony) checkBoolean(
            ClaimUtil.isRegionConnected(claim, civilization),
            "Claim must be connected to existing claim."
        )
        checkBoolean(
            ClaimUtil.regionsInSelection(claim).isEmpty(),
            "You cannot claim with another claimed region inside of your selection."
        )
        val cost = CivUtil.calculateFormulaForCiv(Settings.COST_FORMULA, civilization, claim)
        checkBoolean(
            HookManager.getBalance(getPlayer()) - cost > 0,
            "You do not have enough money to claim this amount of land. Required cost: $cost"
        )
        checkBoolean(
            ClaimUtil.isLocationInRegion(getPlayer().location, claim),
            "You must be standing in your new claim to claim it."
        )
        if (civilization.claims.totalClaimCount == 0)
            checkBoolean(
                !getPlayer().location.subtract(0.0, 1.0, 0.0).block.type.isAir,
                "You be standing on solid ground to claim land"
            )
        if (isColony) {
            checkBoolean(
                civilization.claims.totalClaimCount > 0,
                "You must have at least one regular claim before creating a colony."
            )
            checkBoolean(
                ClaimUtil.distanceFromNearestClaim(
                    claim.center,
                    civilization
                ) > Settings.COLONY_MIN_DISTANCE_FROM_NEAREST_CLAIM,
                "You cannot claim a colony so close to your civilization"
            )
            val maxColonies = CivUtil.calculateFormulaForCiv(Settings.MAX_COLONIES_FORMULA, civilization)
            if (maxColonies.toInt() != -1) checkBoolean(
                civilization.claims.colonyCount < maxColonies,
                "You cannot have more than $maxColonies colonies."
            )
            val colony = Colony(civilization, civilization.claims.idNumber, getPlayer().location)
            civilization.claims.addColony(colony)
            tellSuccess("{1}Claimed colony at your location!")
        } else {
            tellSuccess("{1}Claimed region at your location!")
        }
        if (civilization.claims.totalClaimCount == 0) civilization.home = getPlayer().location
        HookManager.withdraw(getPlayer(), cost)
        player.selection.removeSelection(getPlayer())
        civilization.claims.addClaim(claim)
        Common.callEvent(
            ClaimEvent(
                civilization,
                claim,
                getPlayer()
            )
        )
    }


}