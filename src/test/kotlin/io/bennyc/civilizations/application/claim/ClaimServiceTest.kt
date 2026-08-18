package io.bennyc.civilizations.application.claim

import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.season.SeasonStatus
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

    private data class Fixture(
        val seasons: SeasonService,
        val claims: ClaimService,
        val seasonId: io.bennyc.civilizations.domain.identity.SeasonId,
        val civilizationId: io.bennyc.civilizations.domain.identity.CivilizationId,
    )
}
