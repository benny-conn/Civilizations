/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.util

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.model.Region
import io.bennyc.civilizations.util.MathUtil.replaceVariablesAndCalculateFormula

object CivUtil {

    fun calculateFormulaForCiv(formula: String, civilization: Civilization): Double {
        return replaceVariablesAndCalculateFormula(formula, civilization, null)
    }

    fun calculateFormulaForCiv(formula: String, civilization: Civilization, region: Region): Double {
        return replaceVariablesAndCalculateFormula(formula, civilization, region)
    }
}