package io.bennyc.civilizations.domain.war

import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant

enum class BattleStatus {
    ACTIVE,
    RESOLVING,
    CLOSED,
    CANCELLED,
}

enum class BattleOutcome {
    ATTACKER_VICTORY,
    DEFENDER_VICTORY,
    DRAW,
}

enum class BattleSide {
    ATTACKER,
    DEFENDER,
}

data class Battle(
    val id: BattleId,
    val warId: WarId,
    val seasonId: SeasonId,
    val attackingCivilizationId: CivilizationId,
    val defendingCivilizationId: CivilizationId,
    val triggeredByPlayerId: PlayerId,
    val triggerClaimId: ClaimId,
    val status: BattleStatus,
    val startedAt: Instant,
    val endsAt: Instant,
    val resolvingAt: Instant?,
    val endedAt: Instant?,
    val outcome: BattleOutcome?,
    val winnerCivilizationId: CivilizationId?,
    val updatedAt: Instant,
) {
    init {
        require(attackingCivilizationId != defendingCivilizationId) {
            "A battle requires opposing civilizations"
        }
        require(endsAt > startedAt) { "Battle endsAt must follow startedAt" }
        require(updatedAt >= startedAt) { "Battle updatedAt cannot precede startedAt" }
        require(resolvingAt == null || resolvingAt >= startedAt) {
            "Battle resolvingAt cannot precede startedAt"
        }
        require(endedAt == null || endedAt >= startedAt) {
            "Battle endedAt cannot precede startedAt"
        }
        when (status) {
            BattleStatus.ACTIVE -> {
                require(resolvingAt == null && endedAt == null && outcome == null) {
                    "An active battle cannot contain resolution state"
                }
                require(winnerCivilizationId == null) {
                    "An active battle cannot have a winner"
                }
            }
            BattleStatus.RESOLVING -> {
                requireNotNull(resolvingAt) { "A resolving battle requires resolvingAt" }
                require(endedAt == null && outcome == null && winnerCivilizationId == null) {
                    "A resolving battle cannot contain a terminal result"
                }
            }
            BattleStatus.CLOSED -> {
                requireNotNull(resolvingAt) { "A closed battle requires resolvingAt" }
                requireNotNull(endedAt) { "A closed battle requires endedAt" }
                requireNotNull(outcome) { "A closed battle requires an outcome" }
                require(
                    when (outcome) {
                        BattleOutcome.ATTACKER_VICTORY ->
                            winnerCivilizationId == attackingCivilizationId
                        BattleOutcome.DEFENDER_VICTORY ->
                            winnerCivilizationId == defendingCivilizationId
                        BattleOutcome.DRAW -> winnerCivilizationId == null
                    },
                ) { "Battle winner does not match its outcome" }
            }
            BattleStatus.CANCELLED -> {
                requireNotNull(endedAt) { "A cancelled battle requires endedAt" }
                require(outcome == null && winnerCivilizationId == null) {
                    "A cancelled battle cannot have a result"
                }
            }
        }
    }
}

data class BattleParticipant(
    val seasonId: SeasonId,
    val battleId: BattleId,
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
    val side: BattleSide,
    val joinedAt: Instant,
)
