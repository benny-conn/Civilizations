package io.bennyc.civilizations.application.war

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.economy.EconomyLedger
import io.bennyc.civilizations.application.economy.LedgerTransactionRequest
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransaction
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleCasualty
import io.bennyc.civilizations.domain.war.BattleCasualtyEconomics
import io.bennyc.civilizations.domain.war.BattleCasualtyFunding
import io.bennyc.civilizations.domain.war.BattleCombatResolutionCause
import io.bennyc.civilizations.domain.war.BattleCombatState
import io.bennyc.civilizations.domain.war.BattleCombatant
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleLifeEvent
import io.bennyc.civilizations.domain.war.BattleLifeEventId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleSide
import io.bennyc.civilizations.domain.war.BattleStatus
import java.time.Clock

/** Durable, framework-neutral ordinary battle outcome state. */
class BattleCombatService(
    private val repository: CivilizationsRepository,
    idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
) {
    private val ledger = EconomyLedger(idGenerator, clock)
    /**
     * Consumes one life from every distinct combatant in the batch. Batching makes a
     * same-tick cross-kill deterministic: if both sides lose their final combatant in
     * one request, the result is a draw regardless of event order.
     */
    fun recordLifeLosses(
        request: RecordBattleLifeLosses,
    ): ApplicationResult<BattleCombatUpdate> = repository.transaction {
        val battle = findBattle(request.battleId)
            ?: return@transaction ApplicationResult.Rejected(BattleNotFound(request.battleId))
        val existingEvents = request.losses.associateWith { loss ->
            findBattleLifeEvent(loss.eventId)
        }
        existingEvents.forEach { (loss, existing) ->
            if (existing != null &&
                (existing.battleId != request.battleId || existing.playerId != loss.playerId)
            ) {
                return@transaction ApplicationResult.Rejected(
                    BattleLifeEventConflict(loss.eventId),
                )
            }
        }
        if (existingEvents.values.all { it != null }) {
            return@transaction ApplicationResult.Unchanged(
                currentUpdate(battle, existingEvents.values.filterNotNull()),
            )
        }
        if (battle.status != BattleStatus.ACTIVE) {
            return@transaction ApplicationResult.Rejected(
                BattleCombatUnavailable(battle.id, battle.status),
            )
        }
        val state = findBattleCombatState(battle.id)
            ?: return@transaction ApplicationResult.Rejected(
                BattleCombatStateMissing(battle.id),
            )
        val now = clock.instant()
        if (now >= battle.endsAt) {
            return@transaction ApplicationResult.Rejected(
                BattleCombatWindowExpired(battle.id, battle.endsAt),
            )
        }

        val combatantsByPlayer = listBattleCombatants(battle.id)
            .associateBy(BattleCombatant::playerId)
            .toMutableMap()
        val casualtyEconomics = findBattleCasualtyEconomics(battle.id)
        val insertedEvents = mutableListOf<BattleLifeEvent>()
        val insertedCasualties = mutableListOf<BattleCasualty>()
        for (loss in request.losses) {
            if (existingEvents.getValue(loss) != null) continue
            val current = combatantsByPlayer[loss.playerId]
                ?: return@transaction ApplicationResult.Rejected(
                    PlayerNotBattleCombatant(battle.id, loss.playerId),
                )
            if (current.isEliminated) {
                return@transaction ApplicationResult.Rejected(
                    BattleCombatantAlreadyEliminated(battle.id, loss.playerId),
                )
            }
            val updated = current.copy(
                livesRemaining = current.livesRemaining - 1,
                eliminatedAt = now.takeIf { current.livesRemaining == 1 },
            )
            updateBattleCombatant(updated)
            val event = BattleLifeEvent(
                id = loss.eventId,
                seasonId = battle.seasonId,
                battleId = battle.id,
                playerId = loss.playerId,
                livesBefore = current.livesRemaining,
                livesAfter = updated.livesRemaining,
                recordedAt = now,
            )
            insertBattleLifeEvent(event)
            casualtyEconomics?.let { economics ->
                insertedCasualties += recordCasualty(economics, current, event)
            }
            combatantsByPlayer[loss.playerId] = updated
            insertedEvents += event
        }

        val requestedOutcome = outcomeAfterLosses(combatantsByPlayer.values)
        val updatedBattle: Battle
        val updatedState: BattleCombatState
        if (requestedOutcome == null) {
            updatedBattle = battle
            updatedState = state
        } else {
            updatedBattle = battle.copy(
                status = BattleStatus.RESOLVING,
                resolvingAt = now,
                updatedAt = now,
            ).also(::updateBattle)
            updatedState = state.copy(
                resolutionCause = BattleCombatResolutionCause.ELIMINATION,
                requestedOutcome = requestedOutcome,
                decidedAt = now,
            ).also(::updateBattleCombatState)
        }
        ApplicationResult.Applied(
            BattleCombatUpdate(
                battle = updatedBattle,
                state = updatedState,
                combatants = combatantsByPlayer.values.sortedWith(COMBATANT_ORDER),
                lifeEvents = insertedEvents,
                casualties = insertedCasualties,
            ),
        )
    }

    /**
     * Ends an expired battle with the snapshotted defender-holds outcome. Battles created
     * before combat-state migration remain outcome-neutral for safe legacy recovery.
     */
    fun beginTimeoutResolution(
        battleId: BattleId,
    ): ApplicationResult<BattleTimeoutResolution> = repository.transaction {
        val current = findBattle(battleId)
            ?: return@transaction ApplicationResult.Rejected(BattleNotFound(battleId))
        val state = findBattleCombatState(battleId)
        if (current.status == BattleStatus.RESOLVING) {
            return@transaction ApplicationResult.Unchanged(
                BattleTimeoutResolution(current, state),
            )
        }
        if (current.status != BattleStatus.ACTIVE) {
            return@transaction ApplicationResult.Rejected(
                InvalidBattleTransition(battleId, current.status, BattleStatus.RESOLVING),
            )
        }
        val now = clock.instant()
        if (now < current.endsAt) {
            return@transaction ApplicationResult.Rejected(
                BattleHasNotExpired(battleId, current.endsAt),
            )
        }
        ApplicationResult.Applied(resolveTimeout(current, state, now))
    }

    /** Idempotent startup/refresh recovery driven by absolute timestamps. */
    fun recoverExpiredBattles(seasonId: SeasonId): List<BattleTimeoutResolution> =
        repository.transaction {
            val now = clock.instant()
            listBattlesForSeason(seasonId)
                .asSequence()
                .filter { it.status == BattleStatus.ACTIVE && it.endsAt <= now }
                .map { battle ->
                    resolveTimeout(battle, findBattleCombatState(battle.id), now)
                }
                .toList()
        }

    private fun CivilizationsWriteContext.resolveTimeout(
        battle: Battle,
        state: BattleCombatState?,
        now: java.time.Instant,
    ): BattleTimeoutResolution {
        val resolving = battle.copy(
            status = BattleStatus.RESOLVING,
            resolvingAt = battle.endsAt,
            updatedAt = now,
        ).also(::updateBattle)
        val decidedState = state?.copy(
            resolutionCause = BattleCombatResolutionCause.TIMEOUT,
            requestedOutcome = state.rules.timeoutOutcome,
            decidedAt = battle.endsAt,
        )?.also(::updateBattleCombatState)
        return BattleTimeoutResolution(resolving, decidedState)
    }

    private fun CivilizationsWriteContext.currentUpdate(
        battle: Battle,
        events: List<BattleLifeEvent>,
    ): BattleCombatUpdate = BattleCombatUpdate(
        battle = battle,
        state = findBattleCombatState(battle.id)
            ?: throw IllegalStateException("Battle ${battle.id} life event has no combat state"),
        combatants = listBattleCombatants(battle.id),
        lifeEvents = events,
        casualties = events.mapNotNull { findBattleCasualty(it.id) },
    )

    private fun CivilizationsWriteContext.recordCasualty(
        economics: BattleCasualtyEconomics,
        combatant: BattleCombatant,
        event: BattleLifeEvent,
    ): BattleCasualty {
        val nominalCost = when (combatant.side) {
            BattleSide.ATTACKER -> economics.attackerDeathCost
            BattleSide.DEFENDER -> economics.defenderDeathCost
        }
        val fundedByReserve = combatant.side == BattleSide.ATTACKER &&
            economics.attackerCoverageRequired
        val chargedAmount: MoneyAmount
        val unpaidAmount: MoneyAmount
        val ledgerTransaction = if (fundedByReserve) {
            chargedAmount = nominalCost
            unpaidAmount = MoneyAmount.ZERO
            null
        } else {
            val account = checkNotNull(findCivilizationAccount(combatant.civilizationId)) {
                "Battle ${event.battleId} casualty civilization has no treasury account"
            }
            chargedAmount = MoneyAmount(
                minOf(account.balance.minorUnits, nominalCost.minorUnits),
            )
            unpaidAmount = nominalCost.plus(chargedAmount.negate())
            if (chargedAmount == MoneyAmount.ZERO) {
                null
            } else {
                ledger.post(
                    this,
                    LedgerTransactionRequest(
                        seasonId = event.seasonId,
                        idempotencyKey = "battle-life:${event.id}:casualty-charge",
                        kind = LedgerTransactionKind.BATTLE_CASUALTY_CHARGE,
                        postings = listOf(
                            LedgerPosting(combatant.civilizationId, chargedAmount.negate()),
                        ),
                        referenceType = "BATTLE_LIFE_EVENT",
                        referenceId = event.id.toString(),
                        actorPlayerId = event.playerId,
                        description = "Battle casualty charge for life event ${event.id}",
                    ),
                ).valueOrThrow()
            }
        }
        return BattleCasualty(
            lifeEventId = event.id,
            seasonId = event.seasonId,
            battleId = event.battleId,
            playerId = event.playerId,
            civilizationId = combatant.civilizationId,
            side = combatant.side,
            nominalCost = nominalCost,
            chargedAmount = chargedAmount,
            unpaidAmount = unpaidAmount,
            funding = if (fundedByReserve) {
                BattleCasualtyFunding.ATTACKER_RESERVE
            } else {
                BattleCasualtyFunding.TREASURY
            },
            chargeLedgerTransactionId = ledgerTransaction?.id,
            recordedAt = event.recordedAt,
        ).also(::insertBattleCasualty)
    }

    private fun ApplicationResult<LedgerTransaction>.valueOrThrow(): LedgerTransaction = when (this) {
        is ApplicationResult.Applied -> value
        is ApplicationResult.Unchanged -> value
        is ApplicationResult.Rejected -> error(failure.description)
    }

    private fun outcomeAfterLosses(combatants: Collection<BattleCombatant>): BattleOutcome? {
        val attackersAlive = combatants.any {
            it.side == BattleSide.ATTACKER && !it.isEliminated
        }
        val defendersAlive = combatants.any {
            it.side == BattleSide.DEFENDER && !it.isEliminated
        }
        return when {
            !attackersAlive && !defendersAlive -> BattleOutcome.DRAW
            !attackersAlive -> BattleOutcome.DEFENDER_VICTORY
            !defendersAlive -> BattleOutcome.ATTACKER_VICTORY
            else -> null
        }
    }

    private companion object {
        val COMBATANT_ORDER = compareBy<BattleCombatant>(
            { it.side.name },
            { it.enrolledAt },
            { it.playerId.toString() },
        )
    }
}

data class BattleLifeLoss(
    val eventId: BattleLifeEventId,
    val playerId: PlayerId,
)

data class RecordBattleLifeLosses(
    val battleId: BattleId,
    val losses: List<BattleLifeLoss>,
) {
    init {
        require(losses.isNotEmpty()) { "At least one battle life loss is required" }
        require(losses.map(BattleLifeLoss::eventId).toSet().size == losses.size) {
            "Battle life-event IDs must be unique within a batch"
        }
        require(losses.map(BattleLifeLoss::playerId).toSet().size == losses.size) {
            "A combatant may lose at most one life within one simultaneous batch"
        }
    }
}

data class BattleCombatUpdate(
    val battle: Battle,
    val state: BattleCombatState,
    val combatants: List<BattleCombatant>,
    val lifeEvents: List<BattleLifeEvent>,
    val casualties: List<BattleCasualty> = emptyList(),
)

data class BattleTimeoutResolution(
    val battle: Battle,
    val combatState: BattleCombatState?,
) {
    val requestedOutcome: BattleOutcome?
        get() = combatState?.requestedOutcome
}

data class BattleCombatStateMissing(val battleId: BattleId) : ApplicationFailure {
    override val description: String = "Battle $battleId has no durable combat enrollment"
}

data class BattleCombatUnavailable(
    val battleId: BattleId,
    val status: BattleStatus,
) : ApplicationFailure {
    override val description: String = "Battle $battleId is $status; combat life loss is closed"
}

data class BattleCombatWindowExpired(
    val battleId: BattleId,
    val endsAt: java.time.Instant,
) : ApplicationFailure {
    override val description: String = "Battle $battleId reached its deadline at $endsAt"
}

data class PlayerNotBattleCombatant(
    val battleId: BattleId,
    val playerId: PlayerId,
) : ApplicationFailure {
    override val description: String = "Player $playerId is not a combatant in battle $battleId"
}

data class BattleCombatantAlreadyEliminated(
    val battleId: BattleId,
    val playerId: PlayerId,
) : ApplicationFailure {
    override val description: String = "Player $playerId is already eliminated from battle $battleId"
}

data class BattleLifeEventConflict(
    val eventId: BattleLifeEventId,
) : ApplicationFailure {
    override val description: String = "Battle life event $eventId was already used differently"
}
