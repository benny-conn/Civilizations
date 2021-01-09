/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.util.MathUtil.replaceVariablesAndCalculateFormula

object CivUtil {

    fun calculateFormulaForCiv(formula: String, civilization: Civ): Double {
        return replaceVariablesAndCalculateFormula(formula, civilization, null)
    }

    fun calculateFormulaForCiv(formula: String, civilization: Civ, region: Claim): Double {
        return replaceVariablesAndCalculateFormula(formula, civilization, region)
    }

    fun isPlayerOutlaw(player: CPlayer, civilization: Civ): Boolean {
        return civilization.outlaws.contains(player)
    }
}