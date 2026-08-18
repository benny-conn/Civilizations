package io.bennyc.civilizations.domain.war

import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant

enum class WarStatus {
    DECLARED,
    ACTIVE,
    CLOSED,
    CANCELLED,
}

enum class BattleTrigger {
    HOSTILE_CLAIM_ENTRY,
}

enum class LandDestructionScope {
    OPPOSING_CIVILIZATION_CLAIMS,
}

data class WarRulesSnapshot(
    val battleTrigger: BattleTrigger = BattleTrigger.HOSTILE_CLAIM_ENTRY,
    val destructionScope: LandDestructionScope =
        LandDestructionScope.OPPOSING_CIVILIZATION_CLAIMS,
    val battleDurationSeconds: Long,
) {
    init {
        require(battleDurationSeconds > 0) { "Battle duration must be positive" }
    }
}

data class War(
    val id: WarId,
    val seasonId: SeasonId,
    val declaringCivilizationId: CivilizationId,
    val targetCivilizationId: CivilizationId,
    val declaredByPlayerId: PlayerId,
    val status: WarStatus,
    val rules: WarRulesSnapshot,
    val declaredAt: Instant,
    val activatedAt: Instant?,
    val endedAt: Instant?,
    val updatedAt: Instant,
) {
    init {
        require(declaringCivilizationId != targetCivilizationId) {
            "A civilization cannot declare war on itself"
        }
        require(updatedAt >= declaredAt) { "War updatedAt cannot precede declaredAt" }
        require(activatedAt == null || activatedAt >= declaredAt) {
            "War activatedAt cannot precede declaredAt"
        }
        require(endedAt == null || endedAt >= declaredAt) {
            "War endedAt cannot precede declaredAt"
        }
        when (status) {
            WarStatus.DECLARED -> {
                require(activatedAt == null) { "A declared war cannot have activatedAt" }
                require(endedAt == null) { "A declared war cannot have endedAt" }
            }
            WarStatus.ACTIVE -> {
                requireNotNull(activatedAt) { "An active war requires activatedAt" }
                require(endedAt == null) { "An active war cannot have endedAt" }
            }
            WarStatus.CLOSED -> {
                requireNotNull(activatedAt) { "A closed war requires activatedAt" }
                requireNotNull(endedAt) { "A closed war requires endedAt" }
            }
            WarStatus.CANCELLED -> requireNotNull(endedAt) {
                "A cancelled war requires endedAt"
            }
        }
    }

    val civilizationIds: Set<CivilizationId>
        get() = setOf(declaringCivilizationId, targetCivilizationId)

    fun opponentOf(civilizationId: CivilizationId): CivilizationId? = when (civilizationId) {
        declaringCivilizationId -> targetCivilizationId
        targetCivilizationId -> declaringCivilizationId
        else -> null
    }
}
