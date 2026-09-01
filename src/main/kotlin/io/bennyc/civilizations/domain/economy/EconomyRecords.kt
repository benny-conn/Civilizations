package io.bennyc.civilizations.domain.economy

import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant
import java.util.UUID

@JvmInline
value class LedgerTransactionId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class EconomyBridgeTransferId(val value: UUID) {
    override fun toString(): String = value.toString()
}

data class SeasonEconomySettings(
    val seasonId: SeasonId,
    val currencyScale: CurrencyScale,
    val openingBalance: MoneyAmount,
    val createdAt: Instant,
) {
    init {
        require(openingBalance.minorUnits >= 0) { "Opening balance cannot be negative" }
    }
}

data class CivilizationAccount(
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val balance: MoneyAmount,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(balance.minorUnits >= 0) { "Civilization account balance cannot be negative" }
        require(updatedAt >= createdAt) { "Account updatedAt cannot precede createdAt" }
    }
}

enum class LedgerTransactionKind {
    OPENING_BALANCE,
    PLAYER_DEPOSIT,
    PLAYER_WITHDRAWAL,
    PLAYER_WITHDRAWAL_REVERSAL,
    CIVILIZATION_TRANSFER,
    REPAIR_PAYMENT,
    VICTOR_SHARE,
    BATTLE_CASUALTY_RESERVE,
    BATTLE_CASUALTY_CHARGE,
    BATTLE_CASUALTY_RELEASE,
    ADMIN_ADJUSTMENT,
}

data class LedgerPosting(
    val civilizationId: CivilizationId,
    val amount: MoneyAmount,
)

data class LedgerTransaction(
    val id: LedgerTransactionId,
    val seasonId: SeasonId,
    val idempotencyKey: String,
    val kind: LedgerTransactionKind,
    val referenceType: String?,
    val referenceId: String?,
    val actorPlayerId: PlayerId?,
    val description: String,
    val currencyScale: CurrencyScale,
    val createdAt: Instant,
    val postings: List<LedgerPosting>,
) {
    init {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= MAX_KEY_LENGTH) {
            "Ledger idempotency key must contain 1 through $MAX_KEY_LENGTH characters"
        }
        require(description.isNotBlank() && description.length <= MAX_DESCRIPTION_LENGTH) {
            "Ledger description must contain 1 through $MAX_DESCRIPTION_LENGTH characters"
        }
        require((referenceType == null) == (referenceId == null)) {
            "Ledger reference type and ID must either both be present or both be absent"
        }
        require(referenceType == null || referenceType.isNotBlank()) {
            "Ledger reference type cannot be blank"
        }
        require(referenceId == null || referenceId.isNotBlank()) {
            "Ledger reference ID cannot be blank"
        }
        require(postings.isNotEmpty()) { "A ledger transaction requires at least one posting" }
        require(postings.map(LedgerPosting::civilizationId).toSet().size == postings.size) {
            "A ledger transaction may post to a civilization only once"
        }
    }

    companion object {
        const val MAX_KEY_LENGTH = 160
        const val MAX_DESCRIPTION_LENGTH = 512
    }
}

enum class EconomyBridgeDirection {
    DEPOSIT_TO_CIVILIZATION,
    WITHDRAW_TO_PLAYER,
}

enum class EconomyBridgeStatus {
    PREPARED,
    COMPLETED,
    EXTERNAL_FAILED,
    RECONCILIATION_REQUIRED,
    RECONCILED_CANCELLED,
}

data class EconomyBridgeTransfer(
    val id: EconomyBridgeTransferId,
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val playerId: PlayerId,
    val direction: EconomyBridgeDirection,
    val amount: MoneyAmount,
    val currencyScale: CurrencyScale,
    val providerName: String,
    val idempotencyKey: String,
    val status: EconomyBridgeStatus,
    val ledgerTransactionId: LedgerTransactionId?,
    val reversalTransactionId: LedgerTransactionId?,
    val failureMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
) {
    init {
        require(amount.minorUnits > 0) { "Bridge transfer amount must be positive" }
        require(providerName.isNotBlank() && providerName.length <= MAX_PROVIDER_LENGTH) {
            "Bridge provider name must contain 1 through $MAX_PROVIDER_LENGTH characters"
        }
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= LedgerTransaction.MAX_KEY_LENGTH) {
            "Bridge idempotency key is invalid"
        }
        require(updatedAt >= createdAt) { "Bridge updatedAt cannot precede createdAt" }
        require(completedAt == null || completedAt >= createdAt) {
            "Bridge completedAt cannot precede createdAt"
        }
        require(failureMessage == null || failureMessage.length <= MAX_FAILURE_LENGTH) {
            "Bridge failure message is too long"
        }
        when (direction) {
            EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION -> when (status) {
                EconomyBridgeStatus.PREPARED,
                EconomyBridgeStatus.RECONCILIATION_REQUIRED,
                EconomyBridgeStatus.EXTERNAL_FAILED,
                EconomyBridgeStatus.RECONCILED_CANCELLED,
                -> require(ledgerTransactionId == null && reversalTransactionId == null)
                EconomyBridgeStatus.COMPLETED ->
                    requireNotNull(ledgerTransactionId) { "Completed deposit requires ledger credit" }
            }
            EconomyBridgeDirection.WITHDRAW_TO_PLAYER -> {
                requireNotNull(ledgerTransactionId) { "Withdrawal requires a ledger hold" }
                when (status) {
                    EconomyBridgeStatus.EXTERNAL_FAILED,
                    EconomyBridgeStatus.RECONCILED_CANCELLED,
                    -> requireNotNull(reversalTransactionId) {
                        "Failed withdrawal requires a ledger reversal"
                    }
                    EconomyBridgeStatus.PREPARED,
                    EconomyBridgeStatus.COMPLETED,
                    EconomyBridgeStatus.RECONCILIATION_REQUIRED,
                    -> require(reversalTransactionId == null)
                }
            }
        }
        if (status == EconomyBridgeStatus.COMPLETED ||
            status == EconomyBridgeStatus.EXTERNAL_FAILED ||
            status == EconomyBridgeStatus.RECONCILED_CANCELLED
        ) {
            requireNotNull(completedAt) { "Terminal bridge status requires completedAt" }
        } else {
            require(completedAt == null) { "Open bridge status cannot have completedAt" }
        }
    }

    companion object {
        const val MAX_PROVIDER_LENGTH = 128
        const val MAX_FAILURE_LENGTH = 512
    }
}
