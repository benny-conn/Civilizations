package io.bennyc.civilizations.application.protection

import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.claim.ClaimService
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.economy.BattleCasualtyRules
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.EconomyService
import io.bennyc.civilizations.application.economy.EconomyWithdrawalLockedForLandProtection
import io.bennyc.civilizations.application.economy.LedgerTransactionRequest
import io.bennyc.civilizations.application.economy.PreparePlayerEconomyTransfer
import io.bennyc.civilizations.application.economy.RepairEconomyRules
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.application.support.unchangedValue
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.EconomyBridgeDirection
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.protection.LandProtectionStatus
import io.bennyc.civilizations.domain.protection.ProtectionRepairItemStatus
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LandProtectionServiceTest {
    private val world = WorldId("minecraft:overworld")

    @Test
    fun `upkeep protects a reserve enters bounded exposure and recovers without debt`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val initial = fixture.land.synchronize(fixture.seasonId).appliedValue()
                .single { it.civilizationId == fixture.northId }
            assertEquals(LandProtectionStatus.PROTECTED, initial.status)
            assertEquals(MoneyAmount(300), initial.requiredReserve)

            assertIs<EconomyWithdrawalLockedForLandProtection>(
                fixture.economy.preparePlayerWithdrawal(
                    PreparePlayerEconomyTransfer(
                        fixture.seasonId,
                        fixture.northId,
                        playerId(1),
                        MoneyAmount(701),
                        "TestEconomy",
                        2,
                        "withdraw:below-reserve",
                    ),
                ).rejection(),
            )

            fixture.clock.advanceSeconds(60)
            assertEquals(
                LandProtectionStatus.PROTECTED,
                fixture.land.assess(fixture.northId).appliedValue().status,
            )
            assertEquals(MoneyAmount(800), fixture.balance())
            fixture.adjust(MoneyAmount(-600), "drain-before-next-upkeep")
            fixture.clock.advanceSeconds(60)
            val grace = fixture.land.assess(fixture.northId).appliedValue()
            assertEquals(LandProtectionStatus.GRACE, grace.status)
            assertEquals(MoneyAmount(200), grace.delinquentAmount)
            assertEquals(MoneyAmount(200), fixture.balance())

            fixture.clock.advanceSeconds(61)
            val exposed = fixture.land.assess(fixture.northId).appliedValue()
            assertEquals(LandProtectionStatus.EXPOSED, exposed.status)
            assertEquals(0, exposed.exposureDamageCount)
            assertEquals(2, exposed.exposureDamageLimit)

            val first = fixture.damage(0).appliedValue()
            val second = fixture.damage(1).appliedValue()
            assertEquals(true, first.firstDamageAtSite)
            assertEquals(
                2,
                fixture.land.assess(fixture.northId).unchangedValue().exposureDamageCount,
            )
            assertIs<ExposureDamageLimitReached>(fixture.damage(2).rejection())
            val repeat = fixture.land.prepareMutation(
                fixture.damageRequest(
                    x = 0,
                    observed = SimpleBlockSnapshot("minecraft:air"),
                    expected = SimpleBlockSnapshot("minecraft:dirt"),
                ),
            ).appliedValue()
            assertEquals(2, repeat.event.ordinal)
            assertEquals(false, repeat.firstDamageAtSite)

            fixture.adjust(MoneyAmount(500), "fund-restoration")
            val repairService = ProtectionRepairService(
                database.repository,
                fixture.ids,
                fixture.clock,
                fixture.economyRules,
            )
            val observations = listOf(
                ProtectionDamageObservation(
                    first.site.id,
                    SimpleBlockSnapshot("minecraft:stone"),
                ),
                ProtectionDamageObservation(
                    second.site.id,
                    SimpleBlockSnapshot("minecraft:air"),
                ),
            )
            val assessment = repairService.assess(fixture.northId, observations).appliedValue()
            assertEquals(5_000, assessment.completionBasisPoints)
            assertEquals(1, assessment.repairable.size)
            assertEquals(MoneyAmount(50), assessment.repairableCost)
            assertEquals(
                first.site.id,
                checkNotNull(database.repository.read {
                    findExposureDamageSite(first.site.id)
                }).takeIf { it.resolvedAt != null }?.id,
            )

            val job = repairService.start(
                StartProtectionRepair(
                    civilizationId = fixture.northId,
                    initiatedByPlayerId = playerId(1),
                    targetCompletionBasisPoints = 10_000,
                    observations = observations.filter { it.siteId == second.site.id },
                    idempotencyKey = "protection-repair:test",
                ),
            ).appliedValue()
            assertEquals(2, job.totalDamageCount)
            assertEquals(1, job.observedRestoredCount)
            assertEquals(1, job.selectedCount)
            assertEquals(MoneyAmount(50), job.grossCost)
            val payment = database.repository.read {
                findLedgerTransaction(checkNotNull(job.paymentLedgerTransactionId))!!
            }
            assertEquals(LedgerTransactionKind.LAND_PROTECTION_REPAIR, payment.kind)
            assertEquals(listOf(LedgerPosting(fixture.northId, MoneyAmount(-50))), payment.postings)
            assertEquals(MoneyAmount(650), fixture.balance())
            repairService.begin(job.id).appliedValue()
            val item = repairService.listItems(job.id, null, 1).single()
            val completed = repairService.recordItem(
                job.id,
                item.ordinal,
                ProtectionRepairItemStatus.RESTORED,
            ).appliedValue()
            assertEquals(ProtectionRepairJobStatus.COMPLETED, completed.status)
            assertEquals(1, completed.restoredCount)

            val fullyRestored = repairService.assess(fixture.northId, emptyList()).appliedValue()
            assertEquals(10_000, fullyRestored.completionBasisPoints)
            assertEquals(0, fullyRestored.totalDamageCount)

            val recovered = fixture.land.assess(fixture.northId).appliedValue()
            assertEquals(LandProtectionStatus.PROTECTED, recovered.status)
            assertEquals(MoneyAmount(450), fixture.balance())
        }
    }

    @Test
    fun `partial paid and manual restoration preserve the absolute open exposure basis`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, damageLimit = 5)
            fixture.land.synchronize(fixture.seasonId).appliedValue()
            fixture.clock.advanceSeconds(60)
            fixture.land.assess(fixture.northId).appliedValue()
            fixture.adjust(MoneyAmount(-600), "drain-for-partial-restoration")
            fixture.clock.advanceSeconds(60)
            fixture.land.assess(fixture.northId).appliedValue()
            fixture.clock.advanceSeconds(61)
            fixture.land.assess(fixture.northId).appliedValue()
            fixture.adjust(MoneyAmount(1_000), "fund-partial-restoration")

            val damaged = (0 until 5).map { fixture.damage(it).appliedValue() }
            val repair = ProtectionRepairService(
                database.repository,
                fixture.ids,
                fixture.clock,
                fixture.economyRules,
            )
            val allDamaged = damaged.map {
                ProtectionDamageObservation(it.site.id, SimpleBlockSnapshot("minecraft:air"))
            }
            val half = repair.start(
                StartProtectionRepair(
                    fixture.northId,
                    playerId(1),
                    5_000,
                    allDamaged,
                    "protection-repair:half",
                ),
            ).appliedValue()
            assertEquals(3, half.selectedCount)
            repair.begin(half.id).appliedValue()
            repair.listItems(half.id, null, 10).forEach { item ->
                repair.recordItem(
                    half.id,
                    item.ordinal,
                    ProtectionRepairItemStatus.RESTORED,
                ).appliedValue()
            }

            val remaining = damaged.drop(3)
            val afterManual = repair.assess(
                fixture.northId,
                listOf(
                    ProtectionDamageObservation(
                        remaining[0].site.id,
                        SimpleBlockSnapshot("minecraft:stone"),
                    ),
                    ProtectionDamageObservation(
                        remaining[1].site.id,
                        SimpleBlockSnapshot("minecraft:air"),
                    ),
                ),
            ).appliedValue()
            assertEquals(5, afterManual.totalDamageCount)
            assertEquals(4, afterManual.restoredCount)
            assertEquals(8_000, afterManual.completionBasisPoints)
            assertEquals(1, afterManual.repairable.size)
            assertEquals(MoneyAmount(50), afterManual.repairableCost)

            val finish = repair.start(
                StartProtectionRepair(
                    fixture.northId,
                    playerId(1),
                    10_000,
                    listOf(
                        ProtectionDamageObservation(
                            remaining[1].site.id,
                            SimpleBlockSnapshot("minecraft:air"),
                        ),
                    ),
                    "protection-repair:finish",
                ),
            ).appliedValue()
            assertEquals(5, finish.totalDamageCount)
            assertEquals(4, finish.observedRestoredCount)
            assertEquals(1, finish.selectedCount)
        }
    }

    private fun fixture(database: SqliteTestDatabase, damageLimit: Int = 2): Fixture {
        database.migrator.migrate()
        val ids = SequentialIdGenerator()
        val clock = MutableClock(Instant.parse("2026-08-31T12:00:00Z"))
        val season = SeasonService(database.repository, ids, clock)
            .create("Season One").appliedValue()
        val civilizations = CivilizationService(database.repository, ids, clock)
        val north = civilizations.provision(
            ProvisionCivilization(season.id, "North", playerId(1)),
        ).appliedValue().civilization
        val south = civilizations.provision(
            ProvisionCivilization(season.id, "South", playerId(2)),
        ).appliedValue().civilization
        val economyRules = economyRules()
        val economy = EconomyService(database.repository, ids, clock, economyRules)
        economy.ensureSeasonAccounts(season.id).appliedValue()
        val claim = ClaimService(
            database.repository,
            ids,
            ClaimRules(maxArea = 100, maxClaimsPerCivilization = 4),
            clock = clock,
        ).place(
            PlaceClaim(
                north.id,
                ClaimBounds.between(world, 0, 0, 9, 0),
            ),
        ).appliedValue()
        val land = LandProtectionService(
            database.repository,
            ids,
            clock,
            LandProtectionRules(
                enabled = true,
                intervalSeconds = 60,
                graceSeconds = 60,
                baseCharge = MoneyAmount(100),
                perBlockCharge = MoneyAmount(10),
                baseReserve = MoneyAmount(200),
                perBlockReserve = MoneyAmount(10),
                damageLimitPerExposure = damageLimit,
                assessmentIntervalSeconds = 10,
            ),
        )
        return Fixture(
            database,
            ids,
            clock,
            economyRules,
            economy,
            land,
            season.id,
            north.id,
            south.id,
            claim.id,
        )
    }

    private fun economyRules() = EconomyRules(
        currencyScale = CurrencyScale(2),
        openingCivilizationBalance = MoneyAmount(1_000),
        repair = RepairEconomyRules(
            restoreOriginalUnitPrice = MoneyAmount(50),
            removePlacementUnitPrice = MoneyAmount(25),
            victorShareBasisPoints = 2_500,
            ordinaryInitiatorRoles = setOf(MembershipRole.LEADER),
        ),
        battleCasualties = BattleCasualtyRules(
            attackerDeathCost = MoneyAmount.ZERO,
            defenderDeathCost = MoneyAmount.ZERO,
            requireAttackerCoverage = false,
            lockWithdrawalsDuringBattle = true,
        ),
    )

    private data class Fixture(
        val database: SqliteTestDatabase,
        val ids: SequentialIdGenerator,
        val clock: MutableClock,
        val economyRules: EconomyRules,
        val economy: EconomyService,
        val land: LandProtectionService,
        val seasonId: io.bennyc.civilizations.domain.identity.SeasonId,
        val northId: io.bennyc.civilizations.domain.identity.CivilizationId,
        val southId: io.bennyc.civilizations.domain.identity.CivilizationId,
        val claimId: io.bennyc.civilizations.domain.claim.ClaimId,
    ) {
        fun balance() = database.repository.read {
            findCivilizationAccount(northId)!!.balance
        }

        fun adjust(amount: MoneyAmount, key: String) {
            economy.post(
                LedgerTransactionRequest(
                    seasonId,
                    key,
                    LedgerTransactionKind.ADMIN_ADJUSTMENT,
                    listOf(LedgerPosting(northId, amount)),
                    referenceType = "TEST",
                    referenceId = key,
                    actorPlayerId = null,
                    description = key,
                ),
            ).appliedValue()
        }

        fun damage(x: Int) = land.prepareMutation(damageRequest(x))

        fun damageRequest(
            x: Int,
            observed: SimpleBlockSnapshot = SimpleBlockSnapshot("minecraft:stone"),
            expected: SimpleBlockSnapshot = SimpleBlockSnapshot("minecraft:air"),
        ): PrepareExposureMutation {
            val state = database.repository.read { findLandProtectionState(northId)!! }
            return PrepareExposureMutation(
                exposureId = checkNotNull(state.exposureId),
                ownerCivilizationId = northId,
                claimId = claimId,
                position = BlockPosition3D(WorldId("minecraft:overworld"), x, 64, 0),
                observedState = observed,
                expectedState = expected,
                actorPlayerId = playerId(2),
                actorCivilizationId = southId,
                cause = BlockMutationCause.PLAYER_BREAK,
            )
        }
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }
}
