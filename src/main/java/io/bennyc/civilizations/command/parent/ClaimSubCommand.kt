/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.command.parent

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.model.Colony
import io.bennyc.civilizations.model.Region
import io.bennyc.civilizations.util.CivUtil
import io.bennyc.civilizations.util.ClaimUtil
import io.bennyc.civilizations.util.MathUtil
import kotlinx.coroutines.delay
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.text.DecimalFormat
import kotlin.math.abs


open class ClaimSubCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "claim") {
    override fun onCommand() {
    }


    fun visualize(civPlayer: io.bennyc.civilizations.model.CivPlayer, civilization: Civilization) {
        civPlayer.visualizing = !civPlayer.visualizing
        val visualizedRegions: MutableSet<Region> = HashSet()
        if (args.size > 1 && args[1].equals("here", ignoreCase = true))
            ClaimUtil.getRegionFromLocation(player.location)?.let { visualizedRegions.add(it) }
        else
            visualizedRegions.addAll(civilization.claims.claims)
        if (civPlayer.visualizing) {
            tell(io.bennyc.civilizations.settings.Localization.Notifications.VISUALIZE_START)
        } else {
            tell(io.bennyc.civilizations.settings.Localization.Notifications.VISUALIZE_END)
        }
        io.bennyc.civilizations.AsyncEnvironment.run {
            while (civPlayer.visualizing) {
                for (region in visualizedRegions) {
                    inner@ for (loc in region.boundingBox.filter { player.location.distance(it) < 20 }) {
                        if (!loc.chunk.isLoaded) break@inner
                        io.bennyc.civilizations.settings.Settings.CLAIM_PARTICLE.spawn(player, loc)
                    }
                }
                delay(1000 * io.bennyc.civilizations.settings.Settings.PARTICLE_FREQUENCY.toLong())
            }
        }
    }

    fun claim(
        civilization: Civilization,
        player: io.bennyc.civilizations.model.CivPlayer,
        isColony: Boolean
    ) {
        checkBoolean(
            io.bennyc.civilizations.PermissionChecker.canManageCiv(player, civilization),
            io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV
        )
        checkBoolean(!player.visualizing, io.bennyc.civilizations.settings.Localization.Warnings.Claim.STOP_VISUALIZING)
        checkBoolean(
            player.selection.bothPointsSelected(),
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.INCOMPLETE_SELECTION
        )
        val claim = Region(
            civilization.claims.totalClaimCount,
            player.selection.primary!!,
            player.selection.secondary!!
        )
        val totalArea = abs(MathUtil.areaBetweenTwoPoints(claim.primary, claim.secondary))
        val isPointInOtherRegion =
            ClaimUtil.isLocationInACiv(player.selection.primary!!) || ClaimUtil.isLocationInACiv(player.selection.secondary!!)
        checkBoolean(
            !isPointInOtherRegion,
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.CLAIM_OVERLAP
        )
        val maxClaims =
            CivUtil.calculateFormulaForCiv(io.bennyc.civilizations.settings.Settings.MAX_CLAIMS_FORMULA, civilization)
        if (maxClaims.toInt() != -1) checkBoolean(
            civilization.claims.totalClaimCount < maxClaims,
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.MAX_CLAIMS.replace(
                "{max}",
                maxClaims.toString()
            )
        )
        if (io.bennyc.civilizations.settings.Settings.MAX_BLOCKS_COUNT != -1) checkBoolean(
            civilization.claims.totalBlocksCount + totalArea < io.bennyc.civilizations.settings.Settings.MAX_BLOCKS_COUNT,
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.MAX_BLOCKS.replace(
                "{max}",
                io.bennyc.civilizations.settings.Settings.MAX_BLOCKS_COUNT.toString()
            )
        )
        if (io.bennyc.civilizations.settings.Settings.MAX_CLAIM_SIZE != -1) checkBoolean(
            totalArea < io.bennyc.civilizations.settings.Settings.MAX_CLAIM_SIZE,
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.MAX_CLAIM_SIZE.replace(
                "{max}",
                io.bennyc.civilizations.settings.Settings.MAX_CLAIM_SIZE.toString()
            )
        )
        if (io.bennyc.civilizations.settings.Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM != -1) checkBoolean(
            ClaimUtil.distanceFromNearestClaim(claim.center) > io.bennyc.civilizations.settings.Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM,
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.CIV_DISTANCE
        )
        if (civilization.claims.totalClaimCount > 0 && !isColony) checkBoolean(
            ClaimUtil.isRegionConnected(claim, civilization),
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.CONNECT_CLAIM
        )
        checkBoolean(
            ClaimUtil.regionsInSelection(claim).isEmpty(),
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.CLAIM_IN_CLAIM
        )
        val cost =
            CivUtil.calculateFormulaForCiv(io.bennyc.civilizations.settings.Settings.COST_FORMULA, civilization, claim)
        checkBoolean(
            civilization.bank.balance - cost >= 0,
            io.bennyc.civilizations.settings.Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                "{cost}",
                cost.toString().format(DecimalFormat.getCurrencyInstance())
            )
        )
        checkBoolean(
            claim.isWithin(getPlayer().location),
            io.bennyc.civilizations.settings.Localization.Warnings.Claim.STAND_IN_CLAIM
        )
        if (civilization.claims.totalClaimCount == 0)
            checkBoolean(
                !getPlayer().location.subtract(0.0, 1.0, 0.0).block.type.isAir,
                io.bennyc.civilizations.settings.Localization.Warnings.Claim.SOLID_GROUND
            )
        if (isColony) {
            checkBoolean(
                civilization.claims.totalClaimCount > 0,
                io.bennyc.civilizations.settings.Localization.Warnings.Claim.COLONY_PREREQUISITE
            )
            checkBoolean(
                ClaimUtil.distanceFromNearestClaim(
                    claim.center,
                    civilization
                ) > io.bennyc.civilizations.settings.Settings.COLONY_MIN_DISTANCE_FROM_NEAREST_CLAIM,
                io.bennyc.civilizations.settings.Localization.Warnings.Claim.COLONY_DISTANCE
            )
            val maxColonies = CivUtil.calculateFormulaForCiv(
                io.bennyc.civilizations.settings.Settings.MAX_COLONIES_FORMULA,
                civilization
            )
            if (maxColonies.toInt() != -1) checkBoolean(
                civilization.claims.colonyCount < maxColonies,
                io.bennyc.civilizations.settings.Localization.Warnings.Claim.MAX_COLONIES.replace(
                    "{max}",
                    maxColonies.toString()
                )
            )
            val colony = Colony(civilization, civilization.claims.idNumber, getPlayer().location)
            civilization.claims.addColony(colony)
        }
        if (civilization.claims.totalClaimCount == 0) civilization.home = getPlayer().location
        civilization.bank.removeBalance(cost)
        player.selection.removeSelection(getPlayer())
        civilization.claims.addClaim(claim)
        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
        Common.callEvent(
            io.bennyc.civilizations.event.ClaimEvent(
                civilization,
                claim,
                getPlayer()
            )
        )
    }


}