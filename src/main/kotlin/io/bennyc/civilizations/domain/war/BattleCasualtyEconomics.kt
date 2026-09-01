package io.bennyc.civilizations.domain.war

import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant

/** Economic rules and the attacker's pre-funded maximum liability for one battle. */
data class BattleCasualtyEconomics(
    val seasonId: SeasonId,
    val battleId: BattleId,
    val attackerDeathCost: MoneyAmount,
    val defenderDeathCost: MoneyAmount,
    val attackerCoverageRequired: Boolean,
    val withdrawalsLocked: Boolean,
    val attackerReserve: MoneyAmount,
    val reserveLedgerTransactionId: LedgerTransactionId?,
    val initializedAt: Instant,
    val releasedAmount: MoneyAmount?,
    val releaseLedgerTransactionId: LedgerTransactionId?,
    val releasedAt: Instant?,
) {
    init {
        require(attackerDeathCost.minorUnits >= 0) { "Attacker death cost cannot be negative" }
        require(defenderDeathCost.minorUnits >= 0) { "Defender death cost cannot be negative" }
        require(attackerReserve.minorUnits >= 0) { "Attacker casualty reserve cannot be negative" }
        require((attackerReserve.minorUnits > 0) == (reserveLedgerTransactionId != null)) {
            "A positive attacker casualty reserve requires its ledger debit"
        }
        val open = releasedAmount == null && releaseLedgerTransactionId == null && releasedAt == null
        val released = releasedAmount != null && releasedAt != null
        require(open || released) { "Casualty reserve release fields must be set together" }
        if (releasedAmount != null) {
            require(releasedAmount.minorUnits in 0..attackerReserve.minorUnits) {
                "Released casualty reserve must be within the original reserve"
            }
            require((releasedAmount.minorUnits > 0) == (releaseLedgerTransactionId != null)) {
                "A positive casualty reserve release requires its ledger credit"
            }
            require(releasedAt!! >= initializedAt) {
                "Casualty reserve cannot be released before initialization"
            }
        }
    }

    val isReleased: Boolean
        get() = releasedAt != null
}

enum class BattleCasualtyFunding {
    ATTACKER_RESERVE,
    TREASURY,
}

/** Immutable economic consequence of exactly one durable life-loss event. */
data class BattleCasualty(
    val lifeEventId: BattleLifeEventId,
    val seasonId: SeasonId,
    val battleId: BattleId,
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
    val side: BattleSide,
    val nominalCost: MoneyAmount,
    val chargedAmount: MoneyAmount,
    val unpaidAmount: MoneyAmount,
    val funding: BattleCasualtyFunding,
    val chargeLedgerTransactionId: LedgerTransactionId?,
    val recordedAt: Instant,
) {
    init {
        require(nominalCost.minorUnits >= 0) { "Casualty cost cannot be negative" }
        require(chargedAmount.minorUnits >= 0) { "Casualty charge cannot be negative" }
        require(unpaidAmount.minorUnits >= 0) { "Unpaid casualty amount cannot be negative" }
        require(chargedAmount.plus(unpaidAmount) == nominalCost) {
            "Charged and unpaid casualty amounts must equal the nominal cost"
        }
        when (funding) {
            BattleCasualtyFunding.ATTACKER_RESERVE -> {
                require(side == BattleSide.ATTACKER) {
                    "Only attacker casualties can use attacker coverage"
                }
                require(chargedAmount == nominalCost && unpaidAmount == MoneyAmount.ZERO) {
                    "Attacker coverage must fund the full snapshotted casualty cost"
                }
                require(chargeLedgerTransactionId == null) {
                    "Covered casualties were already debited into the battle reserve"
                }
            }
            BattleCasualtyFunding.TREASURY -> require(
                (chargedAmount.minorUnits > 0) == (chargeLedgerTransactionId != null),
            ) {
                "A positive direct casualty charge requires its ledger transaction"
            }
        }
    }
}
