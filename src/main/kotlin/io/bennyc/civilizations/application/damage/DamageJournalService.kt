package io.bennyc.civilizations.application.damage

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.war.BattleNotFound
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleStatus
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Durably prepares a world mutation without touching Paper state. The caller
 * may mutate the world only after an Applied/Unchanged result commits, and must
 * first confirm that the block still equals [PreparedBlockMutation.expectedCurrentState].
 */
class DamageJournalService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
) {
    fun prepare(request: PrepareBlockMutation): ApplicationResult<PreparedBlockMutation> =
        repository.transaction {
            val battle = findBattle(request.battleId)
                ?: return@transaction ApplicationResult.Rejected(
                    BattleNotFound(request.battleId),
                )
            if (battle.status != BattleStatus.ACTIVE) {
                return@transaction ApplicationResult.Rejected(
                    BattleJournalUnavailable(battle.id, battle.status),
                )
            }
            val season = findSeason(battle.seasonId)
                ?: return@transaction ApplicationResult.Rejected(
                    BattleJournalSeasonMissing(battle.id),
                )
            if (season.status != SeasonStatus.WAR) {
                return@transaction ApplicationResult.Rejected(
                    BattleJournalPhaseClosed(battle.id, season.status),
                )
            }

            val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
            if (now < battle.startedAt || now >= battle.endsAt) {
                return@transaction ApplicationResult.Rejected(
                    BattleJournalOutsideWindow(battle.id, battle.startedAt, battle.endsAt, now),
                )
            }
            val participant = listBattleParticipants(battle.id)
                .singleOrNull { it.playerId == request.actorId }
                ?: return@transaction ApplicationResult.Rejected(
                    ActorNotInBattleJournal(battle.id, request.actorId),
                )
            val claim = findClaim(request.claimId)
                ?: return@transaction ApplicationResult.Rejected(
                    JournalClaimNotFound(request.claimId),
                )
            if (claim.seasonId != battle.seasonId ||
                claim.civilizationId !in setOf(
                    battle.attackingCivilizationId,
                    battle.defendingCivilizationId,
                ) ||
                !claim.bounds.contains(request.position.horizontal())
            ) {
                return@transaction ApplicationResult.Rejected(
                    PositionOutsideBattleLand(
                        battle.id,
                        claim.id,
                        request.position,
                    ),
                )
            }

            val relationship = if (participant.civilizationId == claim.civilizationId) {
                JournalActorRelationship.OWNER
            } else {
                JournalActorRelationship.OPPONENT
            }
            val candidate = BattleBlockChange(
                id = idGenerator.newBlockChangeId(),
                seasonId = battle.seasonId,
                battleId = battle.id,
                claimId = claim.id,
                position = request.position,
                originalState = request.observedState,
                firstMutationCause = request.cause,
                firstActorId = request.actorId,
                recordedAt = now,
            )
            val inserted = insertBlockChangeIfAbsent(candidate)
            val journalEntry = if (inserted) {
                candidate
            } else {
                findBlockChange(battle.id, request.position)
                    ?: throw IllegalStateException(
                        "First-write-wins conflict did not resolve battle ${battle.id} " +
                            "at ${request.position}",
                    )
            }
            val prepared = PreparedBlockMutation(
                journalEntry = journalEntry,
                expectedCurrentState = request.observedState,
                actorId = request.actorId,
                cause = request.cause,
                relationship = relationship,
                preparedAt = now,
                capturedOriginalState = inserted,
            )
            if (inserted) {
                ApplicationResult.Applied(prepared)
            } else {
                ApplicationResult.Unchanged(prepared)
            }
        }
}

data class PrepareBlockMutation(
    val battleId: BattleId,
    val claimId: ClaimId,
    val position: BlockPosition3D,
    val observedState: SimpleBlockSnapshot,
    val actorId: PlayerId,
    val cause: BlockMutationCause,
)

/**
 * A single-use handoff to a future Paper adapter. It is not durable permission:
 * battle/phase authorization must still be live when the mutation is applied.
 */
data class PreparedBlockMutation(
    val journalEntry: BattleBlockChange,
    val expectedCurrentState: SimpleBlockSnapshot,
    val actorId: PlayerId,
    val cause: BlockMutationCause,
    val relationship: JournalActorRelationship,
    val preparedAt: Instant,
    val capturedOriginalState: Boolean,
)

enum class JournalActorRelationship {
    OWNER,
    OPPONENT,
}

data class BattleJournalUnavailable(
    val battleId: BattleId,
    val status: BattleStatus,
) : ApplicationFailure {
    override val description: String = "Battle $battleId is $status; its damage journal is closed"
}

data class BattleJournalSeasonMissing(val battleId: BattleId) : ApplicationFailure {
    override val description: String = "Battle $battleId belongs to a missing season"
}

data class BattleJournalPhaseClosed(
    val battleId: BattleId,
    val status: SeasonStatus,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId cannot journal mutations while its season is $status"
}

data class BattleJournalOutsideWindow(
    val battleId: BattleId,
    val startedAt: Instant,
    val endsAt: Instant,
    val attemptedAt: Instant,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId accepts mutations from $startedAt until $endsAt; got $attemptedAt"
}

data class ActorNotInBattleJournal(
    val battleId: BattleId,
    val playerId: PlayerId,
) : ApplicationFailure {
    override val description: String = "Player $playerId is not a participant in battle $battleId"
}

data class JournalClaimNotFound(val claimId: ClaimId) : ApplicationFailure {
    override val description: String = "Journal claim $claimId does not exist"
}

data class PositionOutsideBattleLand(
    val battleId: BattleId,
    val claimId: ClaimId,
    val position: BlockPosition3D,
) : ApplicationFailure {
    override val description: String =
        "Position $position is not inside claim $claimId for battle $battleId"
}
