package io.bennyc.civilizations.application.economy

import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount

data class EconomyRules(
    val currencyScale: CurrencyScale,
    val openingCivilizationBalance: MoneyAmount,
    val repair: RepairEconomyRules,
    val battleCasualties: BattleCasualtyRules,
) {
    init {
        require(openingCivilizationBalance.minorUnits >= 0) {
            "Opening civilization balance cannot be negative"
        }
    }
}

data class BattleCasualtyRules(
    val attackerDeathCost: MoneyAmount,
    val defenderDeathCost: MoneyAmount,
    val requireAttackerCoverage: Boolean,
    val lockWithdrawalsDuringBattle: Boolean,
) {
    init {
        require(attackerDeathCost.minorUnits >= 0) {
            "Attacker death cost cannot be negative"
        }
        require(defenderDeathCost.minorUnits >= 0) {
            "Defender death cost cannot be negative"
        }
    }
}

data class RepairEconomyRules(
    val restoreOriginalUnitPrice: MoneyAmount,
    val removePlacementUnitPrice: MoneyAmount,
    val victorShareBasisPoints: Int,
    val ordinaryInitiatorRoles: Set<MembershipRole>,
) {
    init {
        require(restoreOriginalUnitPrice.minorUnits >= 0) {
            "Restore-original unit price cannot be negative"
        }
        require(removePlacementUnitPrice.minorUnits >= 0) {
            "Remove-placement unit price cannot be negative"
        }
        require(victorShareBasisPoints in 0..MAX_BASIS_POINTS) {
            "Victor share must be between 0% and 100%"
        }
        require(ordinaryInitiatorRoles.isNotEmpty()) {
            "At least one ordinary repair initiator role is required"
        }
    }

    companion object {
        const val MAX_BASIS_POINTS = 10_000
    }
}
