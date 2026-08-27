package io.bennyc.civilizations.domain.war

import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant

/** Immutable durable reason and requested outcome for a leader surrender. */
data class BattleSurrenderRecord(
    val seasonId: SeasonId,
    val battleId: BattleId,
    val surrenderedCivilizationId: CivilizationId,
    val surrenderedByPlayerId: PlayerId,
    val requestedOutcome: BattleOutcome,
    val surrenderedAt: Instant,
) {
    init {
        require(requestedOutcome != BattleOutcome.DRAW) {
            "A surrender must request victory for the opposing side"
        }
    }
}
