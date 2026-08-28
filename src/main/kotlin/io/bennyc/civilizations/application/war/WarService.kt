package io.bennyc.civilizations.application.war

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationNotFound
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
import io.bennyc.civilizations.application.season.SeasonNotFound
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleCombatRulesSnapshot
import io.bennyc.civilizations.domain.war.BattleCombatState
import io.bennyc.civilizations.domain.war.BattleCombatant
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleParticipant
import io.bennyc.civilizations.domain.war.BattleSide
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.BattleSurrenderRecord
import io.bennyc.civilizations.domain.war.War
import io.bennyc.civilizations.domain.war.WarId
import io.bennyc.civilizations.domain.war.WarRulesSnapshot
import io.bennyc.civilizations.domain.war.WarStatus
import java.time.Clock

class WarService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
) {
    fun declare(request: DeclareWar): ApplicationResult<War> {
        val rules = if (
            request.battleDurationSeconds in 1..MAX_BATTLE_DURATION_SECONDS
        ) {
            WarRulesSnapshot(battleDurationSeconds = request.battleDurationSeconds)
        } else {
            return ApplicationResult.Rejected(
                InvalidBattleDuration(
                    request.battleDurationSeconds,
                    MAX_BATTLE_DURATION_SECONDS,
                ),
            )
        }

        return repository.transaction {
            validateDeclarationPhase(request.seasonId)?.let {
                return@transaction ApplicationResult.Rejected(it)
            }
            val declaring = findCivilization(request.declaringCivilizationId)
                ?: return@transaction ApplicationResult.Rejected(
                    CivilizationNotFound(request.declaringCivilizationId),
                )
            val target = findCivilization(request.targetCivilizationId)
                ?: return@transaction ApplicationResult.Rejected(
                    CivilizationNotFound(request.targetCivilizationId),
                )
            validateWarParty(declaring, request.seasonId)?.let {
                return@transaction ApplicationResult.Rejected(it)
            }
            validateWarParty(target, request.seasonId)?.let {
                return@transaction ApplicationResult.Rejected(it)
            }
            if (declaring.id == target.id) {
                return@transaction ApplicationResult.Rejected(
                    SelfWarNotAllowed(declaring.id),
                )
            }

            val declarer = findMembership(request.seasonId, request.declaredByPlayerId)
            if (declarer?.civilizationId != declaring.id) {
                return@transaction ApplicationResult.Rejected(
                    WarDeclarerMustBeMember(request.declaredByPlayerId, declaring.id),
                )
            }

            val existing = listOpenWarsForCivilization(declaring.id)
                .firstOrNull { war -> target.id in war.civilizationIds }
            if (existing != null &&
                existing.declaringCivilizationId == declaring.id &&
                existing.targetCivilizationId == target.id &&
                existing.declaredByPlayerId == request.declaredByPlayerId &&
                existing.rules == rules
            ) {
                return@transaction ApplicationResult.Unchanged(existing)
            }
            existing?.let { open ->
                return@transaction ApplicationResult.Rejected(
                    WarPairAlreadyOpen(declaring.id, target.id, open.id),
                )
            }

            val now = clock.instant()
            val war = War(
                id = idGenerator.newWarId(),
                seasonId = request.seasonId,
                declaringCivilizationId = declaring.id,
                targetCivilizationId = target.id,
                declaredByPlayerId = request.declaredByPlayerId,
                status = WarStatus.DECLARED,
                rules = rules,
                declaredAt = now,
                activatedAt = null,
                endedAt = null,
                updatedAt = now,
            )
            insertWar(war)
            ApplicationResult.Applied(war)
        }
    }

    fun activate(warId: WarId): ApplicationResult<War> = repository.transaction {
        val current = findWar(warId)
            ?: return@transaction ApplicationResult.Rejected(WarNotFound(warId))
        if (current.status == WarStatus.ACTIVE) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.status != WarStatus.DECLARED) {
            return@transaction ApplicationResult.Rejected(
                InvalidWarTransition(warId, current.status, WarStatus.ACTIVE),
            )
        }
        validateBattlePhase(current.seasonId)?.let {
            return@transaction ApplicationResult.Rejected(it)
        }
        for (civilizationId in current.civilizationIds) {
            val civilization = findCivilization(civilizationId)
                ?: return@transaction ApplicationResult.Rejected(
                    CivilizationNotFound(civilizationId),
                )
            validateWarParty(civilization, current.seasonId)?.let {
                return@transaction ApplicationResult.Rejected(it)
            }
        }

        val now = clock.instant()
        val active = current.copy(
            status = WarStatus.ACTIVE,
            activatedAt = now,
            updatedAt = now,
        )
        updateWar(active)
        ApplicationResult.Applied(active)
    }

    /**
     * Atomically activates a declared war, if needed, and snapshots a battle
     * when one side enters the opposing side's claim during the global WAR phase.
     */
    fun startBattleFromEntry(
        warId: WarId,
        triggeringPlayerId: PlayerId,
        enteredClaimId: ClaimId,
        combatEnrollment: BattleCombatEnrollment? = null,
    ): ApplicationResult<BattleRoster> = repository.transaction {
        var war = findWar(warId)
            ?: return@transaction ApplicationResult.Rejected(WarNotFound(warId))
        if (war.status != WarStatus.DECLARED && war.status != WarStatus.ACTIVE) {
            return@transaction ApplicationResult.Rejected(
                WarNotActive(war.id, war.status),
            )
        }
        validateBattlePhase(war.seasonId)?.let {
            return@transaction ApplicationResult.Rejected(it)
        }

        val entrant = findMembership(war.seasonId, triggeringPlayerId)
            ?: return@transaction ApplicationResult.Rejected(
                BattleTriggerPlayerNotInWar(triggeringPlayerId, war.id),
            )
        val defendingCivilizationId = war.opponentOf(entrant.civilizationId)
            ?: return@transaction ApplicationResult.Rejected(
                BattleTriggerPlayerNotInWar(triggeringPlayerId, war.id),
            )
        val enteredClaim = findClaim(enteredClaimId)
            ?: return@transaction ApplicationResult.Rejected(ClaimNotFoundForBattle(enteredClaimId))
        if (enteredClaim.seasonId != war.seasonId ||
            enteredClaim.civilizationId != defendingCivilizationId
        ) {
            return@transaction ApplicationResult.Rejected(
                EntryIsNotOpponentLand(
                    war.id,
                    triggeringPlayerId,
                    enteredClaim.id,
                    enteredClaim.civilizationId,
                ),
            )
        }
        listBattlesForWar(warId).firstOrNull { it.status.isOpen }?.let { existing ->
            return@transaction ApplicationResult.Unchanged(
                BattleRoster(
                    battle = existing,
                    participants = listBattleParticipants(existing.id),
                    combatState = findBattleCombatState(existing.id),
                    combatants = listBattleCombatants(existing.id),
                ),
            )
        }

        val attackingRoster = listMemberships(entrant.civilizationId)
        val defendingRoster = listMemberships(defendingCivilizationId)
        if (attackingRoster.isEmpty() || defendingRoster.isEmpty()) {
            return@transaction ApplicationResult.Rejected(
                BattleRosterEmpty(
                    if (attackingRoster.isEmpty()) entrant.civilizationId
                    else defendingCivilizationId,
                ),
            )
        }
        listOf(entrant.civilizationId, defendingCivilizationId).forEach { civilizationId ->
            listOpenBattlesForCivilization(civilizationId).firstOrNull()?.let { existing ->
                return@transaction ApplicationResult.Rejected(
                    CivilizationAlreadyInOpenBattle(civilizationId, existing.id),
                )
            }
        }

        val selectedCombatants = combatEnrollment?.let { enrollment ->
            val rostersByPlayer = (attackingRoster + defendingRoster)
                .associateBy { it.playerId }
            if (triggeringPlayerId !in enrollment.playerIds) {
                return@transaction ApplicationResult.Rejected(
                    BattleTriggerMustBeCombatant(triggeringPlayerId),
                )
            }
            val unknown = enrollment.playerIds.firstOrNull { it !in rostersByPlayer }
            if (unknown != null) {
                return@transaction ApplicationResult.Rejected(
                    BattleCombatantNotInRoster(unknown),
                )
            }
            val selected = enrollment.playerIds.map { rostersByPlayer.getValue(it) }
            listOf(entrant.civilizationId, defendingCivilizationId).forEach { civilizationId ->
                if (selected.none { it.civilizationId == civilizationId }) {
                    return@transaction ApplicationResult.Rejected(
                        BattleCombatantSideEmpty(civilizationId),
                    )
                }
            }
            selected
        }

        val now = clock.instant()
        if (war.status == WarStatus.DECLARED) {
            for (civilizationId in war.civilizationIds) {
                val civilization = findCivilization(civilizationId)
                    ?: return@transaction ApplicationResult.Rejected(
                        CivilizationNotFound(civilizationId),
                    )
                validateWarParty(civilization, war.seasonId)?.let {
                    return@transaction ApplicationResult.Rejected(it)
                }
            }
            war = war.copy(
                status = WarStatus.ACTIVE,
                activatedAt = now,
                updatedAt = now,
            ).also(::updateWar)
        }
        val battle = Battle(
            id = idGenerator.newBattleId(),
            warId = war.id,
            seasonId = war.seasonId,
            attackingCivilizationId = entrant.civilizationId,
            defendingCivilizationId = defendingCivilizationId,
            triggeredByPlayerId = triggeringPlayerId,
            triggerClaimId = enteredClaim.id,
            status = BattleStatus.ACTIVE,
            startedAt = now,
            endsAt = now.plusSeconds(war.rules.battleDurationSeconds),
            resolvingAt = null,
            endedAt = null,
            outcome = null,
            winnerCivilizationId = null,
            updatedAt = now,
        )
        val participants = buildList {
            attackingRoster.mapTo(this) { membership ->
                BattleParticipant(
                    seasonId = war.seasonId,
                    battleId = battle.id,
                    playerId = membership.playerId,
                    civilizationId = membership.civilizationId,
                    side = BattleSide.ATTACKER,
                    joinedAt = now,
                )
            }
            defendingRoster.mapTo(this) { membership ->
                BattleParticipant(
                    seasonId = war.seasonId,
                    battleId = battle.id,
                    playerId = membership.playerId,
                    civilizationId = membership.civilizationId,
                    side = BattleSide.DEFENDER,
                    joinedAt = now,
                )
            }
        }
        insertBattle(battle)
        participants.forEach(::insertBattleParticipant)
        val combatState = combatEnrollment?.let { enrollment ->
            BattleCombatState(
                seasonId = battle.seasonId,
                battleId = battle.id,
                rules = enrollment.rules,
                initializedAt = now,
                resolutionCause = null,
                requestedOutcome = null,
                decidedAt = null,
            ).also(::insertBattleCombatState)
        }
        val combatants = if (combatState == null) {
            emptyList()
        } else {
            selectedCombatants.orEmpty().map { membership ->
                BattleCombatant(
                    seasonId = battle.seasonId,
                    battleId = battle.id,
                    playerId = membership.playerId,
                    civilizationId = membership.civilizationId,
                    side = if (membership.civilizationId == battle.attackingCivilizationId) {
                        BattleSide.ATTACKER
                    } else {
                        BattleSide.DEFENDER
                    },
                    initialLives = combatState.rules.livesPerCombatant,
                    livesRemaining = combatState.rules.livesPerCombatant,
                    enrolledAt = now,
                    eliminatedAt = null,
                ).also(::insertBattleCombatant)
            }
        }
        ApplicationResult.Applied(
            BattleRoster(battle, participants, combatState, combatants),
        )
    }

    /**
     * Lets the current leader of either battle civilization end destructive
     * eligibility immediately. The returned outcome is the explicit resolution
     * requested by the surrender; final closure remains a separate resolution step.
     */
    fun surrender(request: SurrenderBattle): ApplicationResult<BattleSurrender> =
        repository.transaction {
            val leader = findMembership(request.seasonId, request.surrenderedByPlayerId)
                ?: return@transaction ApplicationResult.Rejected(
                    SurrendererMustLeadBattleCivilization(request.surrenderedByPlayerId),
                )
            if (leader.role != MembershipRole.LEADER) {
                return@transaction ApplicationResult.Rejected(
                    SurrendererMustLeadBattleCivilization(request.surrenderedByPlayerId),
                )
            }
            val current = listOpenBattlesForCivilization(leader.civilizationId)
                .singleOrNull()
                ?: return@transaction ApplicationResult.Rejected(
                    NoOpenBattleToSurrender(leader.civilizationId),
                )
            if (current.seasonId != request.seasonId) {
                return@transaction ApplicationResult.Rejected(
                    NoActiveBattleToSurrender(leader.civilizationId),
                )
            }
            findBattleSurrender(current.id)?.let { existing ->
                val unchanged = BattleSurrender(
                    battle = current,
                    surrenderedCivilizationId = existing.surrenderedCivilizationId,
                    requestedOutcome = existing.requestedOutcome,
                    surrenderedByPlayerId = existing.surrenderedByPlayerId,
                )
                return@transaction if (
                    existing.surrenderedCivilizationId == leader.civilizationId &&
                    existing.surrenderedByPlayerId == request.surrenderedByPlayerId
                ) {
                    ApplicationResult.Unchanged(unchanged)
                } else {
                    ApplicationResult.Rejected(NoActiveBattleToSurrender(leader.civilizationId))
                }
            }
            if (current.status != BattleStatus.ACTIVE) {
                return@transaction ApplicationResult.Rejected(
                    NoActiveBattleToSurrender(leader.civilizationId),
                )
            }
            val outcome = when (leader.civilizationId) {
                current.attackingCivilizationId -> BattleOutcome.DEFENDER_VICTORY
                current.defendingCivilizationId -> BattleOutcome.ATTACKER_VICTORY
                else -> return@transaction ApplicationResult.Rejected(
                    SurrendererMustLeadBattleCivilization(request.surrenderedByPlayerId),
                )
            }
            val now = clock.instant()
            val resolving = current.copy(
                status = BattleStatus.RESOLVING,
                resolvingAt = now,
                updatedAt = now,
            )
            updateBattle(resolving)
            insertBattleSurrender(
                BattleSurrenderRecord(
                    seasonId = current.seasonId,
                    battleId = current.id,
                    surrenderedCivilizationId = leader.civilizationId,
                    surrenderedByPlayerId = request.surrenderedByPlayerId,
                    requestedOutcome = outcome,
                    surrenderedAt = now,
                ),
            )
            ApplicationResult.Applied(
                BattleSurrender(
                    battle = resolving,
                    surrenderedCivilizationId = leader.civilizationId,
                    requestedOutcome = outcome,
                    surrenderedByPlayerId = request.surrenderedByPlayerId,
                ),
            )
        }

    fun beginResolution(
        battleId: BattleId,
        force: Boolean = false,
    ): ApplicationResult<Battle> = repository.transaction {
        val current = findBattle(battleId)
            ?: return@transaction ApplicationResult.Rejected(BattleNotFound(battleId))
        if (current.status == BattleStatus.RESOLVING) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.status != BattleStatus.ACTIVE) {
            return@transaction ApplicationResult.Rejected(
                InvalidBattleTransition(battleId, current.status, BattleStatus.RESOLVING),
            )
        }
        val now = clock.instant()
        if (!force && now < current.endsAt) {
            return@transaction ApplicationResult.Rejected(
                BattleHasNotExpired(battleId, current.endsAt),
            )
        }
        val resolving = current.copy(
            status = BattleStatus.RESOLVING,
            resolvingAt = if (force) now else current.endsAt,
            updatedAt = now,
        )
        updateBattle(resolving)
        ApplicationResult.Applied(resolving)
    }

    /** Idempotent timestamp-based recovery; safe to call on every runtime refresh. */
    fun recoverExpiredBattles(seasonId: SeasonId): List<Battle> = repository.transaction {
        val now = clock.instant()
        listBattlesForSeason(seasonId)
            .asSequence()
            .filter { it.status == BattleStatus.ACTIVE && it.endsAt <= now }
            .map { battle ->
                battle.copy(
                    status = BattleStatus.RESOLVING,
                    resolvingAt = battle.endsAt,
                    updatedAt = now,
                ).also(::updateBattle)
            }
            .toList()
    }

    fun resolve(
        battleId: BattleId,
        outcome: BattleOutcome,
    ): ApplicationResult<Battle> = repository.transaction {
        val current = findBattle(battleId)
            ?: return@transaction ApplicationResult.Rejected(BattleNotFound(battleId))
        if (current.status == BattleStatus.CLOSED && current.outcome == outcome) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.status != BattleStatus.RESOLVING) {
            return@transaction ApplicationResult.Rejected(
                InvalidBattleTransition(battleId, current.status, BattleStatus.CLOSED),
            )
        }
        if (findDamageReport(battleId) == null) {
            return@transaction ApplicationResult.Rejected(
                BattleDamageReportRequired(battleId),
            )
        }
        val winner = when (outcome) {
            BattleOutcome.ATTACKER_VICTORY -> current.attackingCivilizationId
            BattleOutcome.DEFENDER_VICTORY -> current.defendingCivilizationId
            BattleOutcome.DRAW -> null
        }
        val now = clock.instant()
        val closed = current.copy(
            status = BattleStatus.CLOSED,
            endedAt = now,
            outcome = outcome,
            winnerCivilizationId = winner,
            updatedAt = now,
        )
        updateBattle(closed)
        ApplicationResult.Applied(closed)
    }

    /**
     * Repairs the only unsafe terminal state older adapters could create: a CLOSED battle
     * without a damage report. The original explicit outcome must be repeated, and the
     * battle cannot be reopened over another live engagement for either civilization.
     */
    fun reopenReportlessClosure(
        battleId: BattleId,
        expectedOutcome: BattleOutcome,
    ): ApplicationResult<Battle> = repository.transaction {
        val current = findBattle(battleId)
            ?: return@transaction ApplicationResult.Rejected(BattleNotFound(battleId))
        if (current.status != BattleStatus.CLOSED || current.outcome != expectedOutcome) {
            return@transaction ApplicationResult.Rejected(
                InvalidReportlessBattleRecovery(
                    battleId,
                    current.status,
                    current.outcome,
                    expectedOutcome,
                ),
            )
        }
        if (findDamageReport(battleId) != null) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        for (civilizationId in setOf(
            current.attackingCivilizationId,
            current.defendingCivilizationId,
        )) {
            listOpenBattlesForCivilization(civilizationId).firstOrNull()?.let { open ->
                return@transaction ApplicationResult.Rejected(
                    CivilizationAlreadyInOpenBattle(civilizationId, open.id),
                )
            }
        }
        val now = clock.instant()
        val resolving = current.copy(
            status = BattleStatus.RESOLVING,
            resolvingAt = current.resolvingAt ?: now,
            endedAt = null,
            outcome = null,
            winnerCivilizationId = null,
            updatedAt = now,
        )
        updateBattle(resolving)
        ApplicationResult.Applied(resolving)
    }

    fun cancelBattle(battleId: BattleId): ApplicationResult<Battle> = repository.transaction {
        val current = findBattle(battleId)
            ?: return@transaction ApplicationResult.Rejected(BattleNotFound(battleId))
        if (current.status == BattleStatus.CANCELLED) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.status == BattleStatus.CLOSED) {
            return@transaction ApplicationResult.Rejected(
                InvalidBattleTransition(battleId, current.status, BattleStatus.CANCELLED),
            )
        }
        val now = clock.instant()
        val cancelled = current.copy(
            status = BattleStatus.CANCELLED,
            endedAt = now,
            outcome = null,
            winnerCivilizationId = null,
            updatedAt = now,
        )
        updateBattle(cancelled)
        ApplicationResult.Applied(cancelled)
    }

    fun closeWar(warId: WarId): ApplicationResult<War> = endWar(warId, WarStatus.CLOSED)

    fun cancelWar(warId: WarId): ApplicationResult<War> = endWar(warId, WarStatus.CANCELLED)

    private fun endWar(warId: WarId, target: WarStatus): ApplicationResult<War> =
        repository.transaction {
            val current = findWar(warId)
                ?: return@transaction ApplicationResult.Rejected(WarNotFound(warId))
            if (current.status == target) {
                return@transaction ApplicationResult.Unchanged(current)
            }
            val valid = when (target) {
                WarStatus.CLOSED -> current.status == WarStatus.ACTIVE
                WarStatus.CANCELLED ->
                    current.status == WarStatus.DECLARED || current.status == WarStatus.ACTIVE
                WarStatus.DECLARED,
                WarStatus.ACTIVE,
                -> false
            }
            if (!valid) {
                return@transaction ApplicationResult.Rejected(
                    InvalidWarTransition(warId, current.status, target),
                )
            }
            listBattlesForWar(warId).firstOrNull { it.status.isOpen }?.let { openBattle ->
                return@transaction ApplicationResult.Rejected(
                    WarHasOpenBattle(warId, openBattle.id),
                )
            }
            val now = clock.instant()
            val ended = current.copy(status = target, endedAt = now, updatedAt = now)
            updateWar(ended)
            ApplicationResult.Applied(ended)
        }

    private fun CivilizationsWriteContext.validateDeclarationPhase(
        seasonId: SeasonId,
    ): ApplicationFailure? {
        val season = findSeason(seasonId) ?: return SeasonNotFound(seasonId)
        return if (season.status in DECLARATION_PHASES) {
            null
        } else {
            WarDeclarationPhaseClosed(seasonId, season.status)
        }
    }

    private fun CivilizationsWriteContext.validateBattlePhase(
        seasonId: SeasonId,
    ): ApplicationFailure? {
        val season = findSeason(seasonId) ?: return SeasonNotFound(seasonId)
        return if (season.status == SeasonStatus.WAR) {
            null
        } else {
            WarPhaseClosed(seasonId, season.status)
        }
    }

    private fun validateWarParty(
        civilization: Civilization,
        expectedSeasonId: SeasonId,
    ): ApplicationFailure? = when {
        civilization.seasonId != expectedSeasonId ->
            WarCivilizationSeasonMismatch(
                civilization.id,
                expectedSeasonId,
                civilization.seasonId,
            )
        civilization.status != CivilizationStatus.ACTIVE ->
            WarCivilizationNotActive(civilization.id, civilization.status)
        else -> null
    }

    private val BattleStatus.isOpen: Boolean
        get() = this == BattleStatus.ACTIVE || this == BattleStatus.RESOLVING

    companion object {
        const val MAX_BATTLE_DURATION_SECONDS = 365L * 24L * 60L * 60L
        private val DECLARATION_PHASES = setOf(
            SeasonStatus.SETUP,
            SeasonStatus.PEACE,
            SeasonStatus.WAR,
        )
    }
}

data class DeclareWar(
    val seasonId: SeasonId,
    val declaringCivilizationId: CivilizationId,
    val targetCivilizationId: CivilizationId,
    val declaredByPlayerId: PlayerId,
    val battleDurationSeconds: Long,
)

data class BattleRoster(
    val battle: Battle,
    val participants: List<BattleParticipant>,
    val combatState: BattleCombatState? = null,
    val combatants: List<BattleCombatant> = emptyList(),
)

data class BattleCombatEnrollment(
    val rules: BattleCombatRulesSnapshot,
    val playerIds: Set<PlayerId>,
) {
    init {
        require(playerIds.isNotEmpty()) { "Battle combat enrollment cannot be empty" }
    }
}

data class SurrenderBattle(
    val seasonId: SeasonId,
    val surrenderedByPlayerId: PlayerId,
)

data class BattleSurrender(
    val battle: Battle,
    val surrenderedCivilizationId: CivilizationId,
    val requestedOutcome: BattleOutcome,
    val surrenderedByPlayerId: PlayerId,
)

data class InvalidBattleDuration(
    val suppliedSeconds: Long,
    val maximumSeconds: Long,
) : ApplicationFailure {
    override val description: String =
        "Battle duration must be between 1 and $maximumSeconds seconds; got $suppliedSeconds"
}

data class SelfWarNotAllowed(val civilizationId: CivilizationId) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId cannot declare war on itself"
}

data class WarDeclarerMustBeMember(
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId must belong to civilization $civilizationId to declare war"
}

data class WarPairAlreadyOpen(
    val firstCivilizationId: CivilizationId,
    val secondCivilizationId: CivilizationId,
    val warId: WarId,
) : ApplicationFailure {
    override val description: String =
        "Civilizations $firstCivilizationId and $secondCivilizationId already have open war $warId"
}

data class WarNotFound(val warId: WarId) : ApplicationFailure {
    override val description: String = "War $warId does not exist"
}

data class InvalidWarTransition(
    val warId: WarId,
    val current: WarStatus,
    val requested: WarStatus,
) : ApplicationFailure {
    override val description: String =
        "War $warId cannot transition from $current to $requested"
}

data class WarNotActive(val warId: WarId, val status: WarStatus) : ApplicationFailure {
    override val description: String = "War $warId is $status rather than ACTIVE"
}

data class WarPhaseClosed(
    val seasonId: SeasonId,
    val status: SeasonStatus,
) : ApplicationFailure {
    override val description: String =
        "War operations are closed while season $seasonId is $status"
}

data class WarDeclarationPhaseClosed(
    val seasonId: SeasonId,
    val status: SeasonStatus,
) : ApplicationFailure {
    override val description: String =
        "War declarations are closed while season $seasonId is $status"
}

data class WarCivilizationSeasonMismatch(
    val civilizationId: CivilizationId,
    val expectedSeasonId: SeasonId,
    val actualSeasonId: SeasonId,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId belongs to season $actualSeasonId, not $expectedSeasonId"
}

data class WarCivilizationNotActive(
    val civilizationId: CivilizationId,
    val status: CivilizationStatus,
) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId is $status rather than ACTIVE"
}

data class BattleTriggerPlayerNotInWar(
    val playerId: PlayerId,
    val warId: WarId,
) : ApplicationFailure {
    override val description: String = "Player $playerId is not a member of either side in war $warId"
}

data class ClaimNotFoundForBattle(val claimId: ClaimId) : ApplicationFailure {
    override val description: String = "Battle trigger claim $claimId does not exist"
}

data class EntryIsNotOpponentLand(
    val warId: WarId,
    val playerId: PlayerId,
    val claimId: ClaimId,
    val claimCivilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId entering claim $claimId owned by $claimCivilizationId " +
            "does not trigger war $warId"
}

data class BattleRosterEmpty(val civilizationId: CivilizationId) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId has no roster to snapshot for battle"
}

data class BattleCombatantNotInRoster(val playerId: PlayerId) : ApplicationFailure {
    override val description: String =
        "Player $playerId cannot become a combatant because they are not in the battle roster"
}

data class BattleTriggerMustBeCombatant(val playerId: PlayerId) : ApplicationFailure {
    override val description: String =
        "Triggering player $playerId must be enrolled as a battle combatant"
}

data class BattleCombatantSideEmpty(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId needs at least one eligible online combatant"
}

data class CivilizationAlreadyInOpenBattle(
    val civilizationId: CivilizationId,
    val battleId: BattleId,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId already participates in open battle $battleId"
}

data class BattleNotFound(val battleId: BattleId) : ApplicationFailure {
    override val description: String = "Battle $battleId does not exist"
}

data class InvalidBattleTransition(
    val battleId: BattleId,
    val current: BattleStatus,
    val requested: BattleStatus,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId cannot transition from $current to $requested"
}

data class BattleHasNotExpired(
    val battleId: BattleId,
    val endsAt: java.time.Instant,
) : ApplicationFailure {
    override val description: String = "Battle $battleId does not end until $endsAt"
}

data class BattleDamageReportRequired(
    val battleId: BattleId,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId cannot close until its immutable damage report is sealed"
}

data class InvalidReportlessBattleRecovery(
    val battleId: BattleId,
    val status: BattleStatus,
    val actualOutcome: BattleOutcome?,
    val expectedOutcome: BattleOutcome,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId is $status with $actualOutcome and cannot recover as $expectedOutcome"
}

data class SurrendererMustLeadBattleCivilization(
    val playerId: PlayerId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId must currently lead a civilization in an active battle to surrender"
}

data class NoOpenBattleToSurrender(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId has no open battle to surrender"
}

data class NoActiveBattleToSurrender(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId has no active battle to surrender"
}

data class WarHasOpenBattle(
    val warId: WarId,
    val battleId: BattleId,
) : ApplicationFailure {
    override val description: String = "War $warId still has open battle $battleId"
}
