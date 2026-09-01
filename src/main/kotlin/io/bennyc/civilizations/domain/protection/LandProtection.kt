package io.bennyc.civilizations.domain.protection

import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant
import java.util.UUID

@JvmInline
value class LandExposureId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class LandUpkeepAssessmentId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class ExposureDamageSiteId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class ExposureDamageEventId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class ProtectionRepairJobId(val value: UUID) {
    override fun toString(): String = value.toString()
}

enum class LandProtectionStatus {
    PROTECTED,
    GRACE,
    EXPOSED,
}

data class LandProtectionState(
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val status: LandProtectionStatus,
    val nextAssessmentAt: Instant?,
    val requiredReserve: MoneyAmount,
    val delinquentAmount: MoneyAmount,
    val graceEndsAt: Instant?,
    val exposureId: LandExposureId?,
    val exposureStartedAt: Instant?,
    val exposureDamageLimit: Int?,
    val exposureDamageCount: Int,
    val updatedAt: Instant,
) {
    init {
        require(requiredReserve.minorUnits >= 0) { "Protection reserve cannot be negative" }
        require(delinquentAmount.minorUnits >= 0) { "Delinquent upkeep cannot be negative" }
        require(exposureDamageCount >= 0) { "Exposure damage count cannot be negative" }
        when (status) {
            LandProtectionStatus.PROTECTED -> {
                require(graceEndsAt == null && exposureId == null && exposureStartedAt == null)
                require(exposureDamageLimit == null && exposureDamageCount == 0)
                require(delinquentAmount == MoneyAmount.ZERO)
            }
            LandProtectionStatus.GRACE -> {
                requireNotNull(graceEndsAt)
                require(exposureId == null && exposureStartedAt == null)
                requireNotNull(exposureDamageLimit)
                require(exposureDamageLimit > 0 && exposureDamageCount == 0)
            }
            LandProtectionStatus.EXPOSED -> {
                requireNotNull(graceEndsAt)
                requireNotNull(exposureId)
                requireNotNull(exposureStartedAt)
                requireNotNull(exposureDamageLimit)
                require(exposureDamageLimit > 0)
                require(exposureDamageCount <= exposureDamageLimit)
            }
        }
    }
}

enum class LandUpkeepAssessmentStatus {
    PAID,
    GRACE_STARTED,
    DEFERRED_FOR_BATTLE,
    RECOVERED,
}

/** Immutable audit record for one scheduled charge or recovery payment. */
data class LandUpkeepAssessment(
    val id: LandUpkeepAssessmentId,
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val scheduledAt: Instant,
    val assessedAt: Instant,
    val claimedArea: Long,
    val baseCharge: MoneyAmount,
    val perBlockCharge: MoneyAmount,
    val totalCharge: MoneyAmount,
    val requiredReserve: MoneyAmount,
    val intervalSeconds: Long,
    val graceSeconds: Long,
    val damageLimit: Int,
    val status: LandUpkeepAssessmentStatus,
    val ledgerTransactionId: LedgerTransactionId?,
) {
    init {
        require(claimedArea >= 0)
        require(baseCharge.minorUnits >= 0 && perBlockCharge.minorUnits >= 0)
        require(totalCharge.minorUnits >= 0 && requiredReserve.minorUnits >= 0)
        require(intervalSeconds > 0 && graceSeconds > 0 && damageLimit > 0)
        if (status == LandUpkeepAssessmentStatus.PAID ||
            status == LandUpkeepAssessmentStatus.RECOVERED
        ) {
            require(totalCharge == MoneyAmount.ZERO || ledgerTransactionId != null) {
                "A non-zero paid upkeep assessment requires a ledger transaction"
            }
        } else {
            require(ledgerTransactionId == null) {
                "An unpaid upkeep assessment cannot reference a ledger transaction"
            }
        }
    }
}

data class ExposureDamageSite(
    val id: ExposureDamageSiteId,
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val exposureId: LandExposureId,
    val claimId: ClaimId,
    val position: BlockPosition3D,
    val originalState: SimpleBlockSnapshot,
    val createdAt: Instant,
    val resolvedAt: Instant?,
)

/** Immutable attempted mutation; the world may apply it only after this row commits. */
data class ExposureDamageEvent(
    val id: ExposureDamageEventId,
    val siteId: ExposureDamageSiteId,
    val ordinal: Int,
    val actorPlayerId: PlayerId,
    val actorCivilizationId: CivilizationId,
    val cause: BlockMutationCause,
    val observedState: SimpleBlockSnapshot,
    val expectedState: SimpleBlockSnapshot,
    val recordedAt: Instant,
) {
    init {
        require(ordinal > 0)
    }
}

data class ReportedExposureDamage(
    val site: ExposureDamageSite,
    val latestEvent: ExposureDamageEvent,
)

enum class ProtectionRepairJobStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

enum class ProtectionRepairItemStatus {
    PENDING,
    RESTORED,
    SKIPPED_CONFLICT,
    FAILED,
}

data class ProtectionRepairJob(
    val id: ProtectionRepairJobId,
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val initiatedByPlayerId: PlayerId?,
    val idempotencyKey: String,
    val targetCompletionBasisPoints: Int,
    val totalDamageCount: Long,
    val observedRestoredCount: Long,
    val observedRepairableCount: Long,
    val observedConflictCount: Long,
    val selectedCount: Long,
    val restoreOriginalUnitPrice: MoneyAmount,
    val removePlacementUnitPrice: MoneyAmount,
    val grossCost: MoneyAmount,
    val paymentLedgerTransactionId: LedgerTransactionId?,
    val status: ProtectionRepairJobStatus,
    val nextItemOrdinal: Long,
    val restoredCount: Long,
    val skippedConflictCount: Long,
    val failedCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val failureMessage: String?,
) {
    init {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= MAX_KEY_LENGTH)
        require(targetCompletionBasisPoints in 1..MAX_BASIS_POINTS)
        require(totalDamageCount > 0)
        require(observedRestoredCount >= 0 && observedRepairableCount >= 0 &&
            observedConflictCount >= 0)
        require(
            totalDamageCount == observedRestoredCount + observedRepairableCount +
                observedConflictCount,
        )
        require(selectedCount > 0 && selectedCount <= observedRepairableCount)
        require(restoreOriginalUnitPrice.minorUnits >= 0 &&
            removePlacementUnitPrice.minorUnits >= 0 && grossCost.minorUnits >= 0)
        require(paymentLedgerTransactionId != null || grossCost == MoneyAmount.ZERO)
        require(nextItemOrdinal in 0..selectedCount)
        require(restoredCount >= 0 && skippedConflictCount >= 0 && failedCount >= 0)
        require(restoredCount + skippedConflictCount + failedCount == nextItemOrdinal)
        require(updatedAt >= createdAt)
        require(completedAt == null || completedAt >= createdAt)
        require(failureMessage == null || failureMessage.length <= MAX_FAILURE_LENGTH)
        require(failureMessage == null || status == ProtectionRepairJobStatus.FAILED)
        when (status) {
            ProtectionRepairJobStatus.COMPLETED -> {
                require(nextItemOrdinal == selectedCount && completedAt != null)
            }
            ProtectionRepairJobStatus.CANCELLED,
            ProtectionRepairJobStatus.FAILED,
            -> requireNotNull(completedAt)
            ProtectionRepairJobStatus.PENDING,
            ProtectionRepairJobStatus.RUNNING,
            ProtectionRepairJobStatus.PAUSED,
            -> require(completedAt == null)
        }
    }

    companion object {
        const val MAX_KEY_LENGTH = 160
        const val MAX_FAILURE_LENGTH = 512
        const val MAX_BASIS_POINTS = 10_000
    }
}

data class ProtectionRepairJobItem(
    val jobId: ProtectionRepairJobId,
    val siteId: ExposureDamageSiteId,
    val ordinal: Long,
    val position: BlockPosition3D,
    val expectedState: SimpleBlockSnapshot,
    val restoreState: SimpleBlockSnapshot,
    val unitPrice: MoneyAmount,
    val status: ProtectionRepairItemStatus,
    val processedAt: Instant?,
    val failureMessage: String?,
) {
    init {
        require(ordinal >= 0)
        require(unitPrice.minorUnits >= 0)
        if (status == ProtectionRepairItemStatus.PENDING) {
            require(processedAt == null && failureMessage == null)
        } else {
            requireNotNull(processedAt)
        }
        require(failureMessage == null ||
            failureMessage.length <= ProtectionRepairJob.MAX_FAILURE_LENGTH)
        require(failureMessage == null || status == ProtectionRepairItemStatus.FAILED)
    }
}
