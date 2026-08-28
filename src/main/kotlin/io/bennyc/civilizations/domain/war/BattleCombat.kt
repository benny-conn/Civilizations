package io.bennyc.civilizations.domain.war

import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant
import java.util.UUID

/**
 * A disconnect does not itself change durable battle state. An external combat-logging
 * plugin may turn the logout into a real player/NPC death, which enters through the same
 * life-loss application operation as any other combat death.
 */
enum class BattleDisconnectPolicy {
    RETAIN_LIFE,
}

enum class BattleCombatResolutionCause {
    ELIMINATION,
    TIMEOUT,
}

data class BattleCombatRulesSnapshot(
    val livesPerCombatant: Int,
    val timeoutOutcome: BattleOutcome = BattleOutcome.DEFENDER_VICTORY,
    val disconnectPolicy: BattleDisconnectPolicy = BattleDisconnectPolicy.RETAIN_LIFE,
) {
    init {
        require(livesPerCombatant in 1..MAX_LIVES_PER_COMBATANT) {
            "Combatant lives must be between 1 and $MAX_LIVES_PER_COMBATANT"
        }
        require(timeoutOutcome == BattleOutcome.DEFENDER_VICTORY) {
            "Season One timeout must produce defender victory"
        }
    }

    companion object {
        const val MAX_LIVES_PER_COMBATANT = 10
    }
}

data class BattleCombatState(
    val seasonId: SeasonId,
    val battleId: BattleId,
    val rules: BattleCombatRulesSnapshot,
    val initializedAt: Instant,
    val resolutionCause: BattleCombatResolutionCause?,
    val requestedOutcome: BattleOutcome?,
    val decidedAt: Instant?,
) {
    init {
        val unresolved = resolutionCause == null && requestedOutcome == null && decidedAt == null
        val resolved = resolutionCause != null && requestedOutcome != null && decidedAt != null
        require(unresolved || resolved) {
            "Combat resolution cause, requested outcome, and decision time must be set together"
        }
        require(decidedAt == null || decidedAt >= initializedAt) {
            "Combat decision cannot precede initialization"
        }
        if (resolutionCause == BattleCombatResolutionCause.TIMEOUT) {
            require(requestedOutcome == rules.timeoutOutcome) {
                "Timeout outcome must match the snapshotted combat rules"
            }
        }
    }
}

data class BattleCombatant(
    val seasonId: SeasonId,
    val battleId: BattleId,
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
    val side: BattleSide,
    val initialLives: Int,
    val livesRemaining: Int,
    val enrolledAt: Instant,
    val eliminatedAt: Instant?,
) {
    init {
        require(initialLives in 1..BattleCombatRulesSnapshot.MAX_LIVES_PER_COMBATANT) {
            "Combatant initial lives are outside the supported range"
        }
        require(livesRemaining in 0..initialLives) {
            "Combatant remaining lives must be between zero and initial lives"
        }
        require((livesRemaining == 0) == (eliminatedAt != null)) {
            "A combatant is eliminated exactly when no lives remain"
        }
        require(eliminatedAt == null || eliminatedAt >= enrolledAt) {
            "Combatant elimination cannot precede enrollment"
        }
    }

    val isEliminated: Boolean
        get() = livesRemaining == 0
}

@JvmInline
value class BattleLifeEventId(val value: UUID) {
    override fun toString(): String = value.toString()
}

data class BattleLifeEvent(
    val id: BattleLifeEventId,
    val seasonId: SeasonId,
    val battleId: BattleId,
    val playerId: PlayerId,
    val livesBefore: Int,
    val livesAfter: Int,
    val recordedAt: Instant,
) {
    init {
        require(livesBefore > 0 && livesAfter == livesBefore - 1) {
            "A battle life event must consume exactly one remaining life"
        }
    }
}
