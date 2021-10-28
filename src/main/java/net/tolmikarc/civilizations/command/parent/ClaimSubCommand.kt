/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.parent

import kotlinx.coroutines.delay
import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.api.event.ClaimEvent
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Colony
import net.tolmikarc.civilizations.model.impl.Region
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.ClaimUtil
import net.tolmikarc.civilizations.util.MathUtil
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs


open class ClaimSubCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "claim") {
    override fun onCommand() {
    }


    fun visualize(civPlayer: CPlayer, civilization: Civ) {
        civPlayer.visualizing = !civPlayer.visualizing
        val visualizedRegions: MutableSet<Region> = HashSet()
        if (args.size > 1 && args[1].equals("here", ignoreCase = true))
            ClaimUtil.getRegionFromLocation(player.location)?.let { visualizedRegions.add(it) }
        else
            visualizedRegions.addAll(civilization.claims.claims)
        if (civPlayer.visualizing) {
            tell(Localization.Notifications.VISUALIZE_START)
        } else {
            tell(Localization.Notifications.VISUALIZE_END)
        }
        AsyncEnvironment.run {
            while (civPlayer.visualizing) {
                for (region in visualizedRegions) {
                    inner@ for (loc in region.boundingBox.filter { player.location.distance(it) < 20 }) {
                        if (!loc.chunk.isLoaded) break@inner
                        Settings.CLAIM_PARTICLE.spawn(player, loc)
                    }
                }
                delay(1000 * Settings.PARTICLE_FREQUENCY.toLong())
            }
        }
    }

    fun claim(civilization: Civ, player: CPlayer, isColony: Boolean) {
        checkBoolean(PermissionChecker.canManageCiv(player, civilization), Localization.Warnings.CANNOT_MANAGE_CIV)
        checkBoolean(!player.visualizing, Localization.Warnings.Claim.STOP_VISUALIZING)
        checkBoolean(player.selection.bothPointsSelected(), Localization.Warnings.Claim.INCOMPLETE_SELECTION)
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
            Localization.Warnings.Claim.CLAIM_OVERLAP
        )
        val maxClaims = CivUtil.calculateFormulaForCiv(Settings.MAX_CLAIMS_FORMULA, civilization)
        if (maxClaims.toInt() != -1) checkBoolean(
            civilization.claims.totalClaimCount < maxClaims,
            Localization.Warnings.Claim.MAX_CLAIMS.replace("{max}", maxClaims.toString())
        )
        if (Settings.MAX_BLOCKS_COUNT != -1) checkBoolean(
            civilization.claims.totalBlocksCount + totalArea < Settings.MAX_BLOCKS_COUNT,
            Localization.Warnings.Claim.MAX_BLOCKS.replace("{max}", Settings.MAX_BLOCKS_COUNT.toString())
        )
        if (Settings.MAX_CLAIM_SIZE != -1) checkBoolean(
            totalArea < Settings.MAX_CLAIM_SIZE,
            Localization.Warnings.Claim.MAX_CLAIM_SIZE.replace("{max}", Settings.MAX_CLAIM_SIZE.toString())
        )
        if (Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM != -1) checkBoolean(
            ClaimUtil.distanceFromNearestClaim(claim.center) > Settings.MIN_DISTANCE_FROM_NEAREST_CLAIM,
            Localization.Warnings.Claim.CIV_DISTANCE
        )
        if (civilization.claims.totalClaimCount > 0 && !isColony) checkBoolean(
            ClaimUtil.isRegionConnected(claim, civilization),
            Localization.Warnings.Claim.CONNECT_CLAIM
        )
        checkBoolean(
            ClaimUtil.regionsInSelection(claim).isEmpty(),
            Localization.Warnings.Claim.CLAIM_IN_CLAIM
        )
        val cost = CivUtil.calculateFormulaForCiv(Settings.COST_FORMULA, civilization, claim)
        checkBoolean(
            civilization.bank.balance - cost >= 0,
            Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                "{cost}",
                cost.toString().format(DecimalFormat.getCurrencyInstance())
            )
        )
        checkBoolean(
            claim.isWithin(getPlayer().location),
            Localization.Warnings.Claim.STAND_IN_CLAIM
        )
        if (civilization.claims.totalClaimCount == 0)
            checkBoolean(
                !getPlayer().location.subtract(0.0, 1.0, 0.0).block.type.isAir,
                Localization.Warnings.Claim.SOLID_GROUND
            )
        if (isColony) {
            checkBoolean(
                civilization.claims.totalClaimCount > 0,
                Localization.Warnings.Claim.COLONY_PREREQUISITE
            )
            checkBoolean(
                ClaimUtil.distanceFromNearestClaim(
                    claim.center,
                    civilization
                ) > Settings.COLONY_MIN_DISTANCE_FROM_NEAREST_CLAIM,
                Localization.Warnings.Claim.COLONY_DISTANCE
            )
            val maxColonies = CivUtil.calculateFormulaForCiv(Settings.MAX_COLONIES_FORMULA, civilization)
            if (maxColonies.toInt() != -1) checkBoolean(
                civilization.claims.colonyCount < maxColonies,
                Localization.Warnings.Claim.MAX_COLONIES.replace("{max}", maxColonies.toString())
            )
            val colony = Colony(civilization, civilization.claims.idNumber, getPlayer().location)
            civilization.claims.addColony(colony)
        }
        if (civilization.claims.totalClaimCount == 0) civilization.home = getPlayer().location
        civilization.bank.removeBalance(cost)
        player.selection.removeSelection(getPlayer())
        civilization.claims.addClaim(claim)
        tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
        Common.callEvent(
            ClaimEvent(
                civilization,
                claim,
                getPlayer()
            )
        )
    }


}