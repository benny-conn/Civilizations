/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Claim
import org.bukkit.Location
import org.mineacademy.fo.MathUtil
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.regex.Pattern
import kotlin.math.abs


object MathUtil {

    private val df: DecimalFormat = DecimalFormat("#.##").apply { roundingMode = RoundingMode.DOWN }

    fun doubleToMoney(dubble: Double): Double {
        return df.format(dubble).toDouble()
    }

    fun replaceVariablesAndCalculateFormula(formula: String, civilization: Civ, region: Claim?): Double {
        var replacedVariables = formula
            .replace("{total_claims}", civilization.totalClaimCount.toString())
            .replace("{total_area}", civilization.totalBlocksCount.toString())
            .replace("{citizens}", civilization.citizens.size.toString())
            .replace("{power}", civilization.power.toString())
            .replace("{money}", civilization.bank.balance.toString())
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

    fun isInteger(s: String): Boolean {
        return isInteger(s, 10)
    }

    private fun isInteger(s: String, radix: Int): Boolean {
        if (s.isEmpty()) return false
        for (i in s.indices) {
            if (i == 0 && s[i] == '-') {
                return if (s.length == 1) false else continue
            }
            if (Character.digit(s[i], radix) < 0) return false
        }
        return true
    }

    fun areaBetweenTwoPoints(location1: Location, location2: Location): Int {
        return abs((location1.blockX - location2.blockX) * (location1.blockZ - location2.blockZ))
    }

    fun isPointInRegion(
        x1: Int, y1: Int, x2: Int,
        y2: Int, x: Int, y: Int
    ): Boolean {
        val xBL = x1.coerceAtMost(x2)
        val xTR = x1.coerceAtLeast(x2)
        val yBL = y1.coerceAtMost(y2)
        val yTR = y1.coerceAtLeast(y2)
        return x in xBL..xTR && y in yBL..yTR
    }

    fun isPointInRegion(region: Claim, x: Int, y: Int): Boolean {
        return isPointInRegion(
            region.primary.blockX,
            region.primary.blockZ,
            region.secondary.blockX,
            region.secondary.blockZ,
            x,
            y
        )
    }

    fun isRegionInRegion(regionBig: Claim, regionSmall: Claim): Boolean {
        val xBL = regionBig.primary.blockX.coerceAtMost(regionBig.secondary.blockX)
        val xTR = regionBig.primary.blockX.coerceAtLeast(regionBig.secondary.blockX)
        val yBL = regionBig.primary.blockZ.coerceAtMost(regionBig.secondary.blockZ)
        val yTR = regionBig.primary.blockZ.coerceAtLeast(regionBig.secondary.blockZ)
        return regionSmall.primary.x >= xBL && regionSmall.primary.x <= xTR && regionSmall.primary.z >= yBL && regionSmall.primary.z <= yTR &&
                regionSmall.secondary.x >= xBL && regionSmall.secondary.x <= xTR && regionSmall.secondary.z >= yBL && regionSmall.secondary.z <= yTR

    }
}