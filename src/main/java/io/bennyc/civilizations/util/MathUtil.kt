/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.util

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.model.Region
import org.bukkit.Location
import org.mineacademy.fo.MathUtil
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.regex.Pattern
import kotlin.math.abs


object MathUtil {

    private val df: DecimalFormat = DecimalFormat("#.##").apply {
        roundingMode = RoundingMode.DOWN
        isDecimalSeparatorAlwaysShown = false
    }

    fun doubleToMoney(dubble: Double): Double {
        return df.format(dubble).toDouble()
    }

    fun replaceVariablesAndCalculateFormula(formula: String, civilization: Civilization, region: Region?): Double {
        var replacedVariables = formula
            .replace("{citizens}", civilization.citizens.size.toString())
            .replace("{power}", civilization.power.toString())
            .replace("{money}", civilization.bank.balance.toString())
            .replace("{total_area}", civilization.claims.totalBlocksCount.toString())
            .replace("{total_claims}", civilization.claims.totalClaimCount.toString())
        if (region != null) {
            replacedVariables =
                replacedVariables.replace("{area}", areaBetweenTwoPoints(region.primary, region.secondary).toString())
        }
        return MathUtil.calculate(replacedVariables)
    }


    fun isDouble(s: String?): Boolean {
        val digits = "(\\p{Digit}+)"
        val hexDigits = "(\\p{XDigit}+)"
        val exp = "[eE][+-]?$digits"
        val fpRegex = "[\\x00-\\x20]*" +
                "[+-]?(" +
                "NaN|" +
                "Infinity|" +
                "(((" + digits + "(\\.)?(" + digits + "?)(" + exp + ")?)|" +
                "(\\.(" + digits + ")(" + exp + ")?)|" +
                "((" +
                "(0[xX]" + hexDigits + "(\\.)?)|" +
                "(0[xX]" + hexDigits + "?(\\.)" + hexDigits + ")" +
                ")[pP][+-]?" + digits + "))" +
                "[fFdD]?))" +
                "[\\x00-\\x20]*"
        return Pattern.matches(fpRegex, s)
    }

    fun areaBetweenTwoPoints(location1: Location, location2: Location): Int {
        return abs((location1.blockX - location2.blockX) * (location1.blockZ - location2.blockZ))
    }


    fun isRegionInRegion(regionBig: Region, regionSmall: Region): Boolean {
        val xBL = regionBig.primary.blockX.coerceAtMost(regionBig.secondary.blockX)
        val xTR = regionBig.primary.blockX.coerceAtLeast(regionBig.secondary.blockX)
        val yBL = regionBig.primary.blockZ.coerceAtMost(regionBig.secondary.blockZ)
        val yTR = regionBig.primary.blockZ.coerceAtLeast(regionBig.secondary.blockZ)
        return regionSmall.primary.x >= xBL && regionSmall.primary.x <= xTR && regionSmall.primary.z >= yBL && regionSmall.primary.z <= yTR &&
                regionSmall.secondary.x >= xBL && regionSmall.secondary.x <= xTR && regionSmall.secondary.z >= yBL && regionSmall.secondary.z <= yTR

    }
}