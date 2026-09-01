package io.bennyc.civilizations.domain.claim

import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant
import java.util.UUID

@JvmInline
value class ClaimGroupId(val value: UUID) {
    override fun toString(): String = value.toString()
}

/** One durable connected component of a civilization's territory. */
data class ClaimGroup(
    val id: ClaimGroupId,
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val ordinal: Int,
    val foundedByPlayerId: PlayerId?,
    val establishmentCost: MoneyAmount,
    val requiredMemberCount: Int,
    val requiredTreasuryBalance: MoneyAmount,
    val createdAt: Instant,
) {
    init {
        require(ordinal > 0) { "Claim-group ordinal must be positive" }
        require(establishmentCost.minorUnits >= 0) {
            "Claim-group establishment cost cannot be negative"
        }
        require(requiredMemberCount >= 0) { "Required member count cannot be negative" }
        require(requiredTreasuryBalance.minorUnits >= 0) {
            "Required treasury balance cannot be negative"
        }
    }
}
