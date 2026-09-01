package io.bennyc.civilizations.application.claim

import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.application.economy.BattleCasualtyRules
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.EconomyService
import io.bennyc.civilizations.application.economy.RepairEconomyRules
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClaimServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC)
    private val world = WorldId("minecraft:overworld")

    @Test
    fun `places connected rectangles and persisted claims rebuild the hot index`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val first = fixture.claims.place(
                PlaceClaim(fixture.civilizationId, bounds(0, 0, 9, 9)),
            ).appliedValue()
            val second = fixture.claims.place(
                PlaceClaim(fixture.civilizationId, bounds(10, 2, 19, 7)),
            ).appliedValue()

            val persisted = database.repository.read { listClaimsForSeason(fixture.seasonId) }
            val index = ClaimSpatialIndex(fixture.seasonId, persisted)
            assertEquals(2, persisted.size)
            assertEquals(first, index.claimAt(BlockPosition2D(world, 0, 0)))
            assertEquals(second, index.claimAt(BlockPosition2D(world, 19, 7)))
        }
    }

    @Test
    fun `rejects overlap corner-only contact and oversized rectangles`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.claims.place(
                PlaceClaim(fixture.civilizationId, bounds(0, 0, 9, 9)),
            ).appliedValue()

            assertIs<ClaimOverlapsExisting>(
                fixture.claims.place(
                    PlaceClaim(fixture.civilizationId, bounds(5, -5, 6, 15)),
                ).rejection(),
            )
            assertIs<ClaimIsDisconnected>(
                fixture.claims.place(
                    PlaceClaim(fixture.civilizationId, bounds(10, 10, 19, 19)),
                ).rejection(),
            )
            assertIs<ClaimAreaExceeded>(
                fixture.claims.place(
                    PlaceClaim(fixture.civilizationId, bounds(20, 0, 30, 9)),
                ).rejection(),
            )
            assertEquals(1, database.repository.read { listClaims(fixture.civilizationId).size })
        }
    }

    @Test
    fun `claim count and war phase close further claiming`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, maxClaims = 1)
            fixture.claims.place(
                PlaceClaim(fixture.civilizationId, bounds(0, 0, 9, 9)),
            ).appliedValue()
            assertIs<ClaimCountExceeded>(
                fixture.claims.place(
                    PlaceClaim(fixture.civilizationId, bounds(10, 0, 19, 9)),
                ).rejection(),
            )

            fixture.seasons.transition(fixture.seasonId, SeasonStatus.PEACE).appliedValue()
            fixture.seasons.transition(fixture.seasonId, SeasonStatus.WAR).appliedValue()
            assertIs<ClaimingClosed>(
                fixture.claims.place(
                    PlaceClaim(fixture.civilizationId, bounds(10, 0, 19, 9)),
                ).rejection(),
            )
        }
    }

    @Test
    fun `configured setup-only claim gate closes claiming in peace`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(
                database,
                phaseRules = GameplayPhaseRules(
                    claimCreationAllowedIn = setOf(SeasonStatus.SETUP),
                ),
            )
            fixture.seasons.transition(fixture.seasonId, SeasonStatus.PEACE).appliedValue()

            assertIs<ClaimingClosed>(
                fixture.claims.place(
                    PlaceClaim(fixture.civilizationId, bounds(0, 0, 9, 9)),
                ).rejection(),
            )
        }
    }

    @Test
    fun `ordinary purchases price disconnected groups atomically and bridge groups together`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val ids = SequentialIdGenerator()
            val season = SeasonService(database.repository, ids, clock)
                .create("Season One").appliedValue()
            val civilization = CivilizationService(database.repository, ids, clock).provision(
                ProvisionCivilization(season.id, "North", playerId(1)),
            ).appliedValue().civilization
            val economy = EconomyService(
                database.repository,
                ids,
                clock,
                economyRules(MoneyAmount(10_000)),
            )
            economy.ensureSeasonAccounts(season.id).appliedValue()
            val claims = ClaimService(
                database.repository,
                ids,
                ClaimRules(
                    maxArea = 100,
                    maxClaimsPerCivilization = 8,
                    requireEdgeConnection = false,
                    baseClaimPrice = MoneyAmount(100),
                    pricePerBlock = MoneyAmount(1),
                    groupTiers = listOf(
                        ClaimGroupTier(1),
                        ClaimGroupTier(
                            maxGroups = 2,
                            minimumMembers = 1,
                            minimumTreasuryBalance = MoneyAmount(5_000),
                            establishmentCost = MoneyAmount(1_000),
                        ),
                    ),
                ),
                clock = clock,
            )
            fun purchase(key: String, rectangle: ClaimBounds) = claims.place(
                PlaceClaim(
                    civilization.id,
                    rectangle,
                    actorPlayerId = playerId(1),
                    adminSponsored = false,
                    idempotencyKey = key,
                ),
            ).appliedValue()

            val first = purchase("claim:first", bounds(0, 0, 9, 0))
            val second = purchase("claim:second", bounds(20, 0, 29, 0))
            assertEquals(2, database.repository.read { listClaimGroups(civilization.id).size })
            assertEquals(MoneyAmount(8_780), database.repository.read {
                findCivilizationAccount(civilization.id)!!.balance
            })

            val failedBalance = database.repository.read {
                findCivilizationAccount(civilization.id)!!.balance
            }
            assertIs<ClaimGroupLimitExceeded>(
                claims.place(
                    PlaceClaim(
                        civilization.id,
                        bounds(40, 0, 49, 0),
                        playerId(1),
                        adminSponsored = false,
                        idempotencyKey = "claim:rejected",
                    ),
                ).rejection(),
            )
            assertEquals(failedBalance, database.repository.read {
                findCivilizationAccount(civilization.id)!!.balance
            })
            assertEquals(2, database.repository.read { listClaims(civilization.id).size })

            val bridge = purchase("claim:bridge", bounds(10, 0, 19, 0))
            val persisted = database.repository.read { listClaims(civilization.id) }
            assertEquals(setOf(first.id, second.id, bridge.id), persisted.mapTo(mutableSetOf()) { it.id })
            assertEquals(1, database.repository.read { listClaimGroups(civilization.id).size })
            assertEquals(1, persisted.map { it.groupId }.distinct().size)
            assertEquals(MoneyAmount(8_670), database.repository.read {
                findCivilizationAccount(civilization.id)!!.balance
            })
            assertEquals(
                3,
                database.repository.read {
                    listLedgerTransactionsForCivilization(civilization.id, 20)
                        .count { it.kind == LedgerTransactionKind.CLAIM_PURCHASE }
                },
            )
        }
    }

    private fun fixture(
        database: SqliteTestDatabase,
        maxClaims: Int = 4,
        phaseRules: GameplayPhaseRules = GameplayPhaseRules(),
    ): Fixture {
        database.migrator.migrate()
        val ids = SequentialIdGenerator()
        val seasons = SeasonService(database.repository, ids, clock)
        val season = seasons.create("Season One").appliedValue()
        val civilizations = CivilizationService(database.repository, ids, clock)
        val civilization = civilizations.provision(
            ProvisionCivilization(season.id, "North", playerId(1)),
        ).appliedValue().civilization
        val claims = ClaimService(
            database.repository,
            ids,
            ClaimRules(maxArea = 100, maxClaimsPerCivilization = maxClaims),
            phaseRules,
        )
        return Fixture(seasons, claims, season.id, civilization.id)
    }

    private fun bounds(minX: Int, minZ: Int, maxX: Int, maxZ: Int): ClaimBounds =
        ClaimBounds.between(world, minX, minZ, maxX, maxZ)

    private fun economyRules(openingBalance: MoneyAmount) = EconomyRules(
        currencyScale = CurrencyScale(2),
        openingCivilizationBalance = openingBalance,
        repair = RepairEconomyRules(
            restoreOriginalUnitPrice = MoneyAmount(50),
            removePlacementUnitPrice = MoneyAmount(50),
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
        val seasons: SeasonService,
        val claims: ClaimService,
        val seasonId: io.bennyc.civilizations.domain.identity.SeasonId,
        val civilizationId: io.bennyc.civilizations.domain.identity.CivilizationId,
    )
}
