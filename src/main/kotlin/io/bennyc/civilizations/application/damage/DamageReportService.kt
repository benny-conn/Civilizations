package io.bennyc.civilizations.application.damage

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.persistence.CivilizationsReadContext
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.war.BattleNotFound
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BattleDamageReport
import io.bennyc.civilizations.domain.damage.BattleDamageReportEntry
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.DamageCostCategory
import io.bennyc.civilizations.domain.damage.DamageReportEligibility
import io.bennyc.civilizations.domain.damage.ReportedBattleBlockChange
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleStatus
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Seals the complete, resolution-time outcome of a battle journal.
 *
 * The caller observes final world state on the server thread and passes only
 * framework-neutral values here. The first complete report is immutable;
 * identical retries return it and conflicting retries fail explicitly.
 */
class DamageReportService(
    private val repository: CivilizationsRepository,
    private val clock: Clock,
) {
    /**
     * Loads the immutable application-owned input for a bounded Paper observation pass.
     * SQL is read in cursor pages even though the Paper adapter must ultimately retain the
     * complete observation set required by [generate]. A report already sealed before a
     * restart is returned without re-reading live world state.
     */
    fun loadResolutionBasis(
        battleId: BattleId,
    ): ApplicationResult<DamageResolutionBasis> = repository.read {
        val battle = findBattle(battleId)
            ?: return@read ApplicationResult.Rejected(BattleNotFound(battleId))
        findDamageReport(battleId)?.let { report ->
            return@read ApplicationResult.Unchanged(
                DamageResolutionBasis(battle, emptyList(), report),
            )
        }
        if (battle.status != BattleStatus.RESOLVING) {
            return@read ApplicationResult.Rejected(
                DamageReportUnavailable(battle.id, battle.status),
            )
        }
        ApplicationResult.Applied(
            DamageResolutionBasis(
                battle = battle,
                journal = loadJournal(battle.id),
                sealedReport = null,
            ),
        )
    }

    fun generate(request: GenerateDamageReport): ApplicationResult<BattleDamageReport> {
        val duplicateIds = request.observations
            .groupingBy(FinalBlockObservation::blockChangeId)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            return ApplicationResult.Rejected(
                DuplicateFinalBlockObservations(request.battleId, duplicateIds),
            )
        }

        return repository.transaction {
            val battle = findBattle(request.battleId)
                ?: return@transaction ApplicationResult.Rejected(
                    BattleNotFound(request.battleId),
                )
            val journal = loadJournal(battle.id)
            val observationsById = request.observations.associateBy { it.blockChangeId }
            val journalIds = journal.mapTo(linkedSetOf()) { it.id }
            val missingIds = journalIds - observationsById.keys
            val unexpectedIds = observationsById.keys - journalIds
            if (missingIds.isNotEmpty() || unexpectedIds.isNotEmpty()) {
                return@transaction ApplicationResult.Rejected(
                    DamageReportObservationMismatch(
                        battle.id,
                        missingIds,
                        unexpectedIds,
                    ),
                )
            }

            val existing = findDamageReport(battle.id)
            if (existing != null) {
                val persistedStates = loadReportedChanges(battle.id).associate {
                    it.reportEntry.blockChangeId to it.reportEntry.finalState
                }
                val conflictingIds = observationsById
                    .filter { (id, observation) -> persistedStates[id] != observation.finalState }
                    .keys
                return@transaction if (conflictingIds.isEmpty()) {
                    ApplicationResult.Unchanged(existing)
                } else {
                    ApplicationResult.Rejected(
                        DamageReportAlreadySealed(battle.id, conflictingIds),
                    )
                }
            }

            if (battle.status != BattleStatus.RESOLVING) {
                return@transaction ApplicationResult.Rejected(
                    DamageReportUnavailable(battle.id, battle.status),
                )
            }
            val generatedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS)
            val resolvingAt = requireNotNull(battle.resolvingAt)
            if (generatedAt < resolvingAt) {
                return@transaction ApplicationResult.Rejected(
                    DamageReportBeforeResolution(battle.id, resolvingAt, generatedAt),
                )
            }

            val entries = journal.map { change ->
                change.toReportEntry(observationsById.getValue(change.id).finalState)
            }
            val report = BattleDamageReport(
                seasonId = battle.seasonId,
                battleId = battle.id,
                journaledChangeCount = entries.size.toLong(),
                eligibleChangeCount = entries.count {
                    it.eligibility == DamageReportEligibility.ELIGIBLE
                }.toLong(),
                restoredDuringBattleCount = entries.count {
                    it.eligibility == DamageReportEligibility.RESTORED_DURING_BATTLE
                }.toLong(),
                restoreOriginalBlockCount = entries.count {
                    it.costCategory == DamageCostCategory.RESTORE_ORIGINAL_BLOCK
                }.toLong(),
                removePlacedBlockCount = entries.count {
                    it.costCategory == DamageCostCategory.REMOVE_PLACED_BLOCK
                }.toLong(),
                generatedAt = generatedAt,
            )
            entries.forEach(::insertDamageReportEntry)
            insertDamageReport(report)
            ApplicationResult.Applied(report)
        }
    }

    private fun BattleBlockChange.toReportEntry(
        finalState: SimpleBlockSnapshot,
    ): BattleDamageReportEntry {
        val eligibility = if (finalState == originalState) {
            DamageReportEligibility.RESTORED_DURING_BATTLE
        } else {
            DamageReportEligibility.ELIGIBLE
        }
        val costCategory = when {
            eligibility == DamageReportEligibility.RESTORED_DURING_BATTLE -> null
            originalState.isAirLike -> DamageCostCategory.REMOVE_PLACED_BLOCK
            else -> DamageCostCategory.RESTORE_ORIGINAL_BLOCK
        }
        return BattleDamageReportEntry(
            seasonId = seasonId,
            battleId = battleId,
            blockChangeId = id,
            finalState = finalState,
            eligibility = eligibility,
            costCategory = costCategory,
        )
    }

    private fun CivilizationsReadContext.loadJournal(battleId: BattleId): List<BattleBlockChange> =
        buildList {
            var after: io.bennyc.civilizations.domain.damage.BlockChangeCursor? = null
            do {
                val page = listBlockChanges(battleId, after, REPORT_PAGE_SIZE)
                addAll(page)
                after = page.lastOrNull()?.cursor
            } while (page.size == REPORT_PAGE_SIZE)
        }

    private fun CivilizationsReadContext.loadReportedChanges(
        battleId: BattleId,
    ): List<ReportedBattleBlockChange> = buildList {
        var after: io.bennyc.civilizations.domain.damage.BlockChangeCursor? = null
        do {
            val page = listReportedBlockChanges(battleId, after, REPORT_PAGE_SIZE)
            addAll(page)
            after = page.lastOrNull()?.cursor
        } while (page.size == REPORT_PAGE_SIZE)
    }

    private companion object {
        const val REPORT_PAGE_SIZE = 1_000
    }
}

data class DamageResolutionBasis(
    val battle: Battle,
    val journal: List<BattleBlockChange>,
    val sealedReport: BattleDamageReport?,
)

data class GenerateDamageReport(
    val battleId: BattleId,
    val observations: List<FinalBlockObservation>,
)

data class FinalBlockObservation(
    val blockChangeId: BlockChangeId,
    val finalState: SimpleBlockSnapshot,
)

data class DuplicateFinalBlockObservations(
    val battleId: BattleId,
    val duplicateBlockChangeIds: Set<BlockChangeId>,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId has ${duplicateBlockChangeIds.size} duplicate final observations"
}

data class DamageReportObservationMismatch(
    val battleId: BattleId,
    val missingBlockChangeIds: Set<BlockChangeId>,
    val unexpectedBlockChangeIds: Set<BlockChangeId>,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId final observations are incomplete: " +
            "${missingBlockChangeIds.size} missing and ${unexpectedBlockChangeIds.size} unexpected"
}

data class DamageReportAlreadySealed(
    val battleId: BattleId,
    val conflictingBlockChangeIds: Set<BlockChangeId>,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId already has an immutable report and " +
            "${conflictingBlockChangeIds.size} final observations conflict"
}

data class DamageReportUnavailable(
    val battleId: BattleId,
    val status: BattleStatus,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId is $status; damage reports require RESOLVING"
}

data class DamageReportBeforeResolution(
    val battleId: BattleId,
    val resolvingAt: Instant,
    val generatedAt: Instant,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId began resolving at $resolvingAt but report time was $generatedAt"
}
