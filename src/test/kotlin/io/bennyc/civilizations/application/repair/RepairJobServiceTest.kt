package io.bennyc.civilizations.application.repair

import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.claim.ClaimService
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.damage.DamageJournalService
import io.bennyc.civilizations.application.damage.DamageReportService
import io.bennyc.civilizations.application.damage.FinalBlockObservation
import io.bennyc.civilizations.application.damage.GenerateDamageReport
import io.bennyc.civilizations.application.damage.PrepareBlockMutation
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.EconomyService
import io.bennyc.civilizations.application.economy.InsufficientCivilizationFunds
import io.bennyc.civilizations.application.economy.RepairEconomyRules
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.application.support.unchangedValue
import io.bennyc.civilizations.application.war.DeclareWar
import io.bennyc.civilizations.application.war.WarService
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.repair.RepairFundingMode
import io.bennyc.civilizations.domain.repair.RepairJobItemStatus
import io.bennyc.civilizations.domain.repair.RepairJobStatus
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.infrastructure.persistence.jdbc.JdbcCivilizationsRepository
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RepairJobServiceTest {
    private val world = WorldId("minecraft:overworld")
    private val original = SimpleBlockSnapshot("minecraft:stone")
    private val damaged = SimpleBlockSnapshot("minecraft:air")
    private val altered = SimpleBlockSnapshot("minecraft:granite")
    private val clock = Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `manual rebuilding reduces a later target from fifty to forty seven percent`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, damageCount = 100)
            val allDamaged = fixture.observations()
            assertEquals(
                1,
                fixture.repairs.quote(
                    QuoteRepairRequest(fixture.battleId, fixture.southId, 100, allDamaged),
                ).appliedValue().selectedCount,
            )

            val first = fixture.repairs.create(
                fixture.request("repair:first-half", 5_000, allDamaged),
            ).appliedValue()

            assertEquals(50, first.job.selectedCount)
            assertEquals(MoneyAmount(500), first.job.grossCost)
            assertEquals(MoneyAmount(125), first.job.victorProceeds)
            assertEquals(MoneyAmount(500), fixture.balance(fixture.southId))
            assertEquals(MoneyAmount(1_125), fixture.balance(fixture.northId))
            val paidSelection = first.quote!!.selectedChanges.mapTo(hashSetOf()) {
                it.journalEntry.id
            }
            fixture.complete(first.job.id)

            val manuallyRestored = fixture.changes
                .asSequence()
                .filter { it.id !in paidSelection }
                .take(3)
                .mapTo(hashSetOf()) { it.id }
            val afterManualWork = fixture.observations { change ->
                if (change.id in paidSelection || change.id in manuallyRestored) original else damaged
            }
            val quote = fixture.repairs.quote(
                QuoteRepairRequest(
                    fixture.battleId,
                    fixture.southId,
                    10_000,
                    afterManualWork,
                ),
            ).appliedValue()

            assertEquals(53, quote.assessment.restoredCount)
            assertEquals(47, quote.assessment.repairableCount)
            assertEquals(5_300, quote.assessment.completionBasisPoints)
            assertEquals(47, quote.selectedCount)
            assertEquals(MoneyAmount(470), quote.grossCost)

            val second = fixture.repairs.create(
                fixture.request("repair:remaining", 10_000, afterManualWork),
            ).appliedValue()
            assertEquals(47, second.job.selectedCount)
            assertEquals(MoneyAmount(30), fixture.balance(fixture.southId))
            assertEquals(MoneyAmount(1_242), fixture.balance(fixture.northId))
        }
    }

    @Test
    fun `selection is deterministic and altered blocks are never purchasable`() {
        SqliteTestDatabase().use { firstDatabase ->
            val first = fixture(firstDatabase, damageCount = 20)
            val observations = first.observations { change ->
                if (change == first.changes.first()) altered else damaged
            }
            val quote = first.repairs.quote(
                QuoteRepairRequest(first.battleId, first.southId, 5_000, observations),
            ).appliedValue()
            assertEquals(1, quote.assessment.conflictCount)
            assertEquals(10, quote.selectedCount)
            assertNotEquals(first.changes.first().id, quote.selectedChanges.first().journalEntry.id)
            assertIs<RepairTargetUnreachable>(
                first.repairs.quote(
                    QuoteRepairRequest(first.battleId, first.southId, 10_000, observations),
                ).rejection(),
            )

            val repeated = first.repairs.quote(
                QuoteRepairRequest(first.battleId, first.southId, 5_000, observations),
            ).appliedValue()
            assertEquals(
                quote.selectedChanges.map { it.journalEntry.id },
                repeated.selectedChanges.map { it.journalEntry.id },
            )
        }
    }

    @Test
    fun `repair payment is all or nothing when treasury cannot afford it`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, damageCount = 100, unitPrice = 20)

            assertIs<InsufficientCivilizationFunds>(
                fixture.repairs.create(
                    fixture.request("repair:too-expensive", 10_000, fixture.observations()),
                ).rejection(),
            )

            assertEquals(MoneyAmount(1_000), fixture.balance(fixture.southId))
            assertNull(database.repository.read {
                findRepairJobByIdempotencyKey("repair:too-expensive")
            })
        }
    }

    @Test
    fun `confirmed repair never charges above the displayed maximum`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, damageCount = 10, unitPrice = 10)
            val request = fixture.request(
                "repair:confirmed-price",
                10_000,
                fixture.observations(),
            )

            val rejected = fixture.repairs.create(
                request,
                maximumGrossCost = MoneyAmount(99),
            ).rejection()

            assertEquals(
                RepairConfirmationPriceExceeded(MoneyAmount(99), MoneyAmount(100)),
                rejected,
            )
            assertEquals(MoneyAmount(1_000), fixture.balance(fixture.southId))
            assertNull(database.repository.read {
                findRepairJobByIdempotencyKey("repair:confirmed-price")
            })

            val created = fixture.repairs.create(
                request,
                maximumGrossCost = MoneyAmount(100),
            ).appliedValue()
            assertEquals(MoneyAmount(100), created.job.grossCost)
            assertEquals(MoneyAmount(900), fixture.balance(fixture.southId))

            assertIs<RepairConfirmationPriceExceeded>(
                fixture.repairs.create(
                    request,
                    maximumGrossCost = MoneyAmount(99),
                ).rejection(),
            )
            assertEquals(MoneyAmount(900), fixture.balance(fixture.southId))
        }
    }

    @Test
    fun `restore and placement removal prices are snapshotted separately`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(
                database,
                damageCount = 2,
                unitPrice = 10,
                removeUnitPrice = 30,
                placementCount = 1,
            )

            val created = fixture.repairs.create(
                fixture.request("repair:mixed-categories", 10_000, fixture.observations()),
            ).appliedValue()

            assertEquals(1, created.job.selectedRestoreOriginalCount)
            assertEquals(1, created.job.selectedRemovePlacementCount)
            assertEquals(MoneyAmount(10), created.job.restoreOriginalUnitPrice)
            assertEquals(MoneyAmount(30), created.job.removePlacementUnitPrice)
            assertEquals(MoneyAmount(40), created.job.grossCost)
            assertEquals(
                setOf(MoneyAmount(10), MoneyAmount(30)),
                database.repository.read {
                    listRepairJobItems(created.job.id, null, 10).map { it.unitPrice }.toSet()
                },
            )
        }
    }

    @Test
    fun `zero victor share and admin sponsorship remain configurable and audited`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, damageCount = 10, victorShareBasisPoints = 0)
            val ordinary = fixture.repairs.create(
                fixture.request("repair:no-share", 5_000, fixture.observations()),
            ).appliedValue()
            assertEquals(MoneyAmount.ZERO, ordinary.job.victorProceeds)
            assertEquals(MoneyAmount(1_000), fixture.balance(fixture.northId))
            fixture.complete(ordinary.job.id)

            val restored = ordinary.quote!!.selectedChanges.mapTo(hashSetOf()) {
                it.journalEntry.id
            }
            val admin = fixture.repairs.create(
                fixture.request(
                    key = "repair:admin",
                    targetBasisPoints = 10_000,
                    observations = fixture.observations {
                        if (it.id in restored) original else damaged
                    },
                    fundingMode = RepairFundingMode.ADMIN_SPONSORED,
                ),
            ).appliedValue()
            assertEquals(MoneyAmount.ZERO, admin.job.grossCost)
            assertEquals(MoneyAmount.ZERO, admin.job.victorProceeds)
            assertNull(admin.job.paymentLedgerTransactionId)
            assertEquals(playerId(3), admin.job.initiatedByPlayerId)
            assertEquals(MoneyAmount(950), fixture.balance(fixture.southId))
        }
    }

    @Test
    fun `idempotent creation does not charge a second time`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, damageCount = 10)
            val request = fixture.request("repair:retry", 5_000, fixture.observations())
            val created = fixture.repairs.create(request).appliedValue()

            val replay = fixture.repairs.create(request).unchangedValue()

            assertEquals(created.job, replay.job)
            assertEquals(created.payment?.id, replay.payment?.id)
            assertEquals(created.payment?.postings?.toSet(), replay.payment?.postings?.toSet())
            assertEquals(MoneyAmount(950), fixture.balance(fixture.southId))
            assertEquals(1, database.repository.read {
                listRepairJobsForBattle(fixture.battleId, 10).size
            })
        }
    }

    @Test
    fun `cursor results persist and startup pauses interrupted execution`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, damageCount = 20)
            val job = fixture.repairs.create(
                fixture.request("repair:cursor", 10_000, fixture.observations()),
            ).appliedValue().job
            fixture.repairs.startExecution(job.id).appliedValue()
            val firstBatch = fixture.repairs.loadWorkBatch(job.id, 5).appliedValue()
            val results = firstBatch.items.mapIndexed { index, work ->
                when (index) {
                    3 -> RepairWorkResult(
                        work.item.blockChangeId,
                        work.item.ordinal,
                        RepairJobItemStatus.SKIPPED_CONFLICT,
                    )
                    4 -> RepairWorkResult(
                        work.item.blockChangeId,
                        work.item.ordinal,
                        RepairJobItemStatus.FAILED,
                        "test failure",
                    )
                    else -> RepairWorkResult(
                        work.item.blockChangeId,
                        work.item.ordinal,
                        RepairJobItemStatus.RESTORED,
                    )
                }
            }
            val progressed = fixture.repairs.recordWorkBatch(
                RecordRepairWorkBatch(job.id, results),
            ).appliedValue()
            assertEquals(5, progressed.nextItemOrdinal)

            val restarted = RepairJobService(
                JdbcCivilizationsRepository(database.connectionFactory),
                fixture.ids,
                clock,
                fixture.rules,
            )
            assertEquals(1, restarted.recoverInterruptedJobs())
            assertEquals(0, restarted.recoverInterruptedJobs())
            val paused = restarted.find(job.id).appliedValue()
            assertEquals(RepairJobStatus.PAUSED, paused.status)
            assertEquals(5, paused.nextItemOrdinal)
            assertEquals(3, paused.restoredCount)
            assertEquals(1, paused.skippedConflictCount)
            assertEquals(1, paused.failedCount)

            restarted.startExecution(job.id).appliedValue()
            val remaining = restarted.loadWorkBatch(job.id, 20).appliedValue()
            assertEquals(15, remaining.items.size)
            assertEquals(5, remaining.items.first().item.ordinal)
            val completed = restarted.recordWorkBatch(
                RecordRepairWorkBatch(
                    job.id,
                    remaining.items.map {
                        RepairWorkResult(
                            it.item.blockChangeId,
                            it.item.ordinal,
                            RepairJobItemStatus.RESTORED,
                        )
                    },
                ),
            ).appliedValue()
            assertEquals(RepairJobStatus.COMPLETED, completed.status)
            assertEquals(20, completed.nextItemOrdinal)
            assertEquals(18, completed.restoredCount)
        }
    }

    private fun fixture(
        database: SqliteTestDatabase,
        damageCount: Int,
        unitPrice: Long = 10,
        removeUnitPrice: Long = unitPrice,
        placementCount: Int = 0,
        victorShareBasisPoints: Int = 2_500,
    ): Fixture {
        database.migrator.migrate()
        val ids = SequentialIdGenerator()
        val rules = EconomyRules(
            currencyScale = CurrencyScale(2),
            openingCivilizationBalance = MoneyAmount(1_000),
            repair = RepairEconomyRules(
                restoreOriginalUnitPrice = MoneyAmount(unitPrice),
                removePlacementUnitPrice = MoneyAmount(removeUnitPrice),
                victorShareBasisPoints = victorShareBasisPoints,
                ordinaryInitiatorRoles = setOf(MembershipRole.LEADER),
            ),
        )
        val seasons = SeasonService(database.repository, ids, clock)
        val season = seasons.create("Season One").appliedValue()
        val civilizations = CivilizationService(database.repository, ids, clock)
        val north = civilizations.provision(
            ProvisionCivilization(season.id, "North", playerId(1), setOf(playerId(2))),
        ).appliedValue().civilization
        val south = civilizations.provision(
            ProvisionCivilization(season.id, "South", playerId(3), setOf(playerId(4))),
        ).appliedValue().civilization
        val claims = ClaimService(
            database.repository,
            ids,
            ClaimRules(maxArea = 256, maxClaimsPerCivilization = 4),
        )
        claims.place(
            PlaceClaim(north.id, ClaimBounds.between(world, 0, 0, 15, 15)),
        ).appliedValue()
        val southClaim = claims.place(
            PlaceClaim(south.id, ClaimBounds.between(world, 32, 0, 47, 15)),
        ).appliedValue()
        val economy = EconomyService(database.repository, ids, clock, rules)
        economy.ensureSeasonAccounts(season.id).appliedValue()
        seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
        seasons.transition(season.id, SeasonStatus.WAR).appliedValue()
        val wars = WarService(database.repository, ids, clock)
        val war = wars.declare(
            DeclareWar(season.id, north.id, south.id, playerId(1), 60),
        ).appliedValue()
        wars.activate(war.id).appliedValue()
        val battle = wars.startBattleFromEntry(war.id, playerId(2), southClaim.id)
            .appliedValue().battle
        val journal = DamageJournalService(database.repository, ids, clock)
        val changes = (0 until damageCount).map { index ->
            val isPlacement = index < placementCount
            journal.prepare(
                PrepareBlockMutation(
                    battle.id,
                    southClaim.id,
                    BlockPosition3D(
                        world,
                        32 + index % 16,
                        72,
                        index / 16,
                    ),
                    if (isPlacement) SimpleBlockSnapshot("minecraft:air") else original,
                    playerId(2),
                    if (isPlacement) {
                        BlockMutationCause.PLAYER_PLACE
                    } else {
                        BlockMutationCause.PLAYER_BREAK
                    },
                ),
            ).appliedValue().journalEntry
        }
        val sealedDamagedStates = changes.associate { change ->
            change.id to if (change.journalEntryOriginalIsAir()) {
                SimpleBlockSnapshot("minecraft:cobblestone")
            } else {
                damaged
            }
        }
        wars.beginResolution(battle.id, force = true).appliedValue()
        DamageReportService(database.repository, clock).generate(
            GenerateDamageReport(
                battle.id,
                changes.map { FinalBlockObservation(it.id, sealedDamagedStates.getValue(it.id)) },
            ),
        ).appliedValue()
        wars.resolve(battle.id, BattleOutcome.ATTACKER_VICTORY).appliedValue()
        return Fixture(
            database,
            ids,
            rules,
            RepairJobService(database.repository, ids, clock, rules),
            battle.id,
            north.id,
            south.id,
            changes,
            sealedDamagedStates,
        )
    }

    private fun BattleBlockChange.journalEntryOriginalIsAir(): Boolean = originalState.isAirLike

    private inner class Fixture(
        val database: SqliteTestDatabase,
        val ids: SequentialIdGenerator,
        val rules: EconomyRules,
        val repairs: RepairJobService,
        val battleId: io.bennyc.civilizations.domain.war.BattleId,
        val northId: CivilizationId,
        val southId: CivilizationId,
        val changes: List<BattleBlockChange>,
        val sealedDamagedStates: Map<io.bennyc.civilizations.domain.damage.BlockChangeId, SimpleBlockSnapshot>,
    ) {
        fun observations(
            state: ((BattleBlockChange) -> SimpleBlockSnapshot)? = null,
        ): List<CurrentRepairObservation> = changes.map {
            CurrentRepairObservation(
                it.id,
                state?.invoke(it) ?: sealedDamagedStates.getValue(it.id),
            )
        }

        fun request(
            key: String,
            targetBasisPoints: Int,
            observations: List<CurrentRepairObservation>,
            fundingMode: RepairFundingMode = RepairFundingMode.ORDINARY,
        ) = CreateRepairJobRequest(
            battleId,
            southId,
            playerId(3),
            fundingMode,
            targetBasisPoints,
            observations,
            key,
        )

        fun balance(civilizationId: CivilizationId): MoneyAmount =
            checkNotNull(database.repository.read { findCivilizationAccount(civilizationId) }).balance

        fun complete(jobId: io.bennyc.civilizations.domain.repair.RepairJobId) {
            repairs.startExecution(jobId).appliedValue()
            val work = repairs.loadWorkBatch(jobId, 1_000).appliedValue()
            repairs.recordWorkBatch(
                RecordRepairWorkBatch(
                    jobId,
                    work.items.map {
                        RepairWorkResult(
                            it.item.blockChangeId,
                            it.item.ordinal,
                            RepairJobItemStatus.RESTORED,
                        )
                    },
                ),
            ).appliedValue()
        }
    }
}
