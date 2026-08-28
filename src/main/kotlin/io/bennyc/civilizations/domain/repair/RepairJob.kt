package io.bennyc.civilizations.domain.repair

import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.BattleId
import java.time.Instant
import java.util.UUID

@JvmInline
value class RepairJobId(val value: UUID) {
    override fun toString(): String = value.toString()
}

enum class RepairFundingMode {
    ORDINARY,
    ADMIN_SPONSORED,
}

enum class RepairJobStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

enum class RepairJobItemStatus {
    PENDING,
    RESTORED,
    SKIPPED_CONFLICT,
    FAILED,
}

/** Durable plan and progress summary; Paper world mutation is performed by a later runner. */
data class RepairJob(
    val id: RepairJobId,
    val seasonId: SeasonId,
    val battleId: BattleId,
    val civilizationId: CivilizationId,
    val initiatedByPlayerId: PlayerId?,
    val fundingMode: RepairFundingMode,
    val idempotencyKey: String,
    val targetCompletionBasisPoints: Int,
    val totalEligibleCount: Long,
    val observedRestoredCount: Long,
    val observedRepairableCount: Long,
    val observedConflictCount: Long,
    val selectedRestoreOriginalCount: Long,
    val selectedRemovePlacementCount: Long,
    val restoreOriginalUnitPrice: MoneyAmount,
    val removePlacementUnitPrice: MoneyAmount,
    val grossCost: MoneyAmount,
    val victorShareBasisPoints: Int,
    val victorCivilizationId: CivilizationId?,
    val victorProceeds: MoneyAmount,
    val paymentLedgerTransactionId: LedgerTransactionId?,
    val status: RepairJobStatus,
    val nextItemOrdinal: Long,
    val restoredCount: Long,
    val skippedConflictCount: Long,
    val failedCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val failureMessage: String?,
) {
    val selectedCount: Long
        get() = selectedRestoreOriginalCount + selectedRemovePlacementCount

    init {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= MAX_KEY_LENGTH) {
            "Repair idempotency key must contain 1 through $MAX_KEY_LENGTH characters"
        }
        require(targetCompletionBasisPoints in 1..MAX_BASIS_POINTS) {
            "Repair target completion must be greater than 0% and at most 100%"
        }
        require(totalEligibleCount > 0) { "Repair job requires eligible battle damage" }
        require(observedRestoredCount >= 0 && observedRepairableCount >= 0 &&
            observedConflictCount >= 0
        ) { "Observed repair counts cannot be negative" }
        require(
            totalEligibleCount == observedRestoredCount + observedRepairableCount +
                observedConflictCount,
        ) { "Observed repair counts must cover all eligible damage" }
        require(selectedRestoreOriginalCount >= 0 && selectedRemovePlacementCount >= 0) {
            "Selected repair counts cannot be negative"
        }
        require(selectedCount > 0 && selectedCount <= observedRepairableCount) {
            "Repair job selection must contain currently repairable damage"
        }
        require(restoreOriginalUnitPrice.minorUnits >= 0 &&
            removePlacementUnitPrice.minorUnits >= 0 && grossCost.minorUnits >= 0
        ) { "Repair prices cannot be negative" }
        require(victorShareBasisPoints in 0..MAX_BASIS_POINTS) {
            "Victor share must be between 0% and 100%"
        }
        require(victorProceeds.minorUnits in 0..grossCost.minorUnits) {
            "Victor proceeds cannot exceed the repair cost"
        }
        if (victorCivilizationId == null) {
            require(victorProceeds == MoneyAmount.ZERO) {
                "Repair without a victor cannot produce victor proceeds"
            }
        } else {
            require(victorCivilizationId != civilizationId) {
                "A civilization cannot receive proceeds from its own repair"
            }
        }
        when (fundingMode) {
            RepairFundingMode.ORDINARY -> {
                val pricedCost = restoreOriginalUnitPrice
                    .times(selectedRestoreOriginalCount)
                    .plus(removePlacementUnitPrice.times(selectedRemovePlacementCount))
                require(grossCost == pricedCost) {
                    "Ordinary repair cost must equal its selected snapshotted units"
                }
                requireNotNull(paymentLedgerTransactionId) {
                    "Ordinary repair requires an atomic ledger payment"
                }
            }
            RepairFundingMode.ADMIN_SPONSORED -> {
                require(grossCost == MoneyAmount.ZERO && victorProceeds == MoneyAmount.ZERO) {
                    "Admin-sponsored repair is payment-free"
                }
                require(paymentLedgerTransactionId == null) {
                    "Admin-sponsored repair cannot reference a payment"
                }
            }
        }
        require(nextItemOrdinal in 0..selectedCount) { "Repair cursor is outside its selection" }
        require(restoredCount >= 0 && skippedConflictCount >= 0 && failedCount >= 0) {
            "Repair result counts cannot be negative"
        }
        require(restoredCount + skippedConflictCount + failedCount == nextItemOrdinal) {
            "Repair result counts must equal the durable cursor"
        }
        require(updatedAt >= createdAt) { "Repair update cannot precede creation" }
        require(completedAt == null || completedAt >= createdAt) {
            "Repair completion cannot precede creation"
        }
        require(failureMessage == null || failureMessage.length <= MAX_FAILURE_LENGTH) {
            "Repair failure message is too long"
        }
        require(failureMessage == null || status == RepairJobStatus.FAILED) {
            "Only a failed repair job may have a failure message"
        }
        when (status) {
            RepairJobStatus.COMPLETED -> {
                require(nextItemOrdinal == selectedCount && completedAt != null) {
                    "Completed repair must finish every selected item"
                }
            }
            RepairJobStatus.CANCELLED,
            RepairJobStatus.FAILED,
            -> requireNotNull(completedAt) { "Terminal repair requires completedAt" }
            RepairJobStatus.QUEUED,
            RepairJobStatus.RUNNING,
            RepairJobStatus.PAUSED,
            -> require(completedAt == null) { "Open repair cannot have completedAt" }
        }
    }

    companion object {
        const val MAX_KEY_LENGTH = 160
        const val MAX_FAILURE_LENGTH = 512
        const val MAX_BASIS_POINTS = 10_000
    }
}

data class RepairJobItem(
    val repairJobId: RepairJobId,
    val battleId: BattleId,
    val blockChangeId: BlockChangeId,
    val ordinal: Long,
    val unitPrice: MoneyAmount,
    val status: RepairJobItemStatus,
    val processedAt: Instant?,
    val failureMessage: String?,
) {
    init {
        require(ordinal >= 0) { "Repair item ordinal cannot be negative" }
        require(unitPrice.minorUnits >= 0) { "Repair item price cannot be negative" }
        if (status == RepairJobItemStatus.PENDING) {
            require(processedAt == null && failureMessage == null) {
                "Pending repair item cannot have a result"
            }
        } else {
            requireNotNull(processedAt) { "Processed repair item requires a timestamp" }
        }
        require(failureMessage == null || failureMessage.length <= RepairJob.MAX_FAILURE_LENGTH) {
            "Repair item failure message is too long"
        }
        require(failureMessage == null || status == RepairJobItemStatus.FAILED) {
            "Only a failed repair item may have a failure message"
        }
    }
}
