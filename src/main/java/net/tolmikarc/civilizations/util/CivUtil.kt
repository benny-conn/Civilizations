/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.util.MathUtil.replaceVariablesAndCalculateFormula
import org.mineacademy.fo.region.Region

object CivUtil {

    fun calculateFormulaForCiv(formula: String, civilization: Civilization): Double {
        return replaceVariablesAndCalculateFormula(formula, civilization, null)
    }

    fun calculateFormulaForCiv(formula: String, civilization: Civilization, region: Region): Double {
        return replaceVariablesAndCalculateFormula(formula, civilization, region)
    }

    fun isPlayerOutlaw(player: CivPlayer, civilization: Civilization): Boolean {
        return civilization.outlaws.contains(player)
    }
}