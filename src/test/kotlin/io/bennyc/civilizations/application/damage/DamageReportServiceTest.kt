package io.bennyc.civilizations.application.damage

import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.claim.ClaimService
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.application.support.unchangedValue
import io.bennyc.civilizations.application.war.DeclareWar
import io.bennyc.civilizations.application.war.WarService
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.DamageCostCategory
import io.bennyc.civilizations.domain.damage.DamageReportEligibility
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.infrastructure.persistence.jdbc.JdbcCivilizationsRepository
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DamageReportServiceTest {
    private val world = WorldId("minecraft:overworld")

    @Test
    fun `seals deterministic eligibility and cost categories across restart`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val restore = fixture.prepare(40, "minecraft:stone", BlockMutationCause.PLAYER_BREAK)
            val remove = fixture.prepare(41, "minecraft:air", BlockMutationCause.PLAYER_PLACE)
            val noOp = fixture.prepare(42, "minecraft:dirt", BlockMutationCause.EXPLOSION)
            fixture.wars.beginResolution(fixture.battle.id, force = true).appliedValue()
            val request = GenerateDamageReport(
                fixture.battle.id,
                listOf(
                    restore.observation("minecraft:air"),
                    remove.observation("minecraft:cobblestone"),
                    noOp.observation("minecraft:dirt"),
                ),
            )

            val report = fixture.reports.generate(request).appliedValue()

            assertEquals(3, report.journaledChangeCount)
            assertEquals(2, report.eligibleChangeCount)
            assertEquals(2, report.baseRepairUnitCount)
            assertEquals(1, report.restoredDuringBattleCount)
            assertEquals(1, report.restoreOriginalBlockCount)
            assertEquals(1, report.removePlacedBlockCount)
            val firstPage = database.repository.read {
                listReportedBlockChanges(fixture.battle.id, after = null, limit = 2)
            }
            val secondPage = database.repository.read {
                listReportedBlockChanges(
                    fixture.battle.id,
                    after = firstPage.last().cursor,
                    limit = 2,
                )
            }
            val entries = (firstPage + secondPage).associateBy { it.journalEntry.id }
            assertEquals(DamageReportEligibility.ELIGIBLE, entries.getValue(restore.id).reportEntry.eligibility)
            assertEquals(
                DamageCostCategory.RESTORE_ORIGINAL_BLOCK,
                entries.getValue(restore.id).reportEntry.costCategory,
            )
            assertEquals(
                DamageCostCategory.REMOVE_PLACED_BLOCK,
                entries.getValue(remove.id).reportEntry.costCategory,
            )
            assertEquals(
                DamageReportEligibility.RESTORED_DURING_BATTLE,
                entries.getValue(noOp.id).reportEntry.eligibility,
            )
            assertNull(entries.getValue(noOp.id).reportEntry.costCategory)

            val restarted = DamageReportService(
                JdbcCivilizationsRepository(database.connectionFactory),
                fixture.clock,
            )
            assertEquals(report, restarted.generate(request).unchangedValue())
            val conflict = request.copy(
                observations = request.observations.map {
                    if (it.blockChangeId == restore.id) {
                        it.copy(finalState = SimpleBlockSnapshot("minecraft:granite"))
                    } else {
                        it
                    }
                },
            )
            assertIs<DamageReportAlreadySealed>(restarted.generate(conflict).rejection())

            assertFailsWith<SQLException> {
                database.connectionFactory.open().use { connection ->
                    connection.prepareStatement(
                        "UPDATE battle_damage_reports SET eligible_change_count = 0 " +
                            "WHERE battle_id = ?",
                    ).use { statement ->
                        statement.setString(1, fixture.battle.id.toString())
                        statement.executeUpdate()
                    }
                }
            }
            assertFailsWith<SQLException> {
                database.connectionFactory.open().use { connection ->
                    connection.prepareStatement(
                        "DELETE FROM battle_damage_report_entries WHERE battle_id = ?",
                    ).use { statement ->
                        statement.setString(1, fixture.battle.id.toString())
                        statement.executeUpdate()
                    }
                }
            }
        }
    }

    @Test
    fun `requires resolving battle and one final observation for every journal row`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val change = fixture.prepare(40, "minecraft:stone", BlockMutationCause.PLAYER_BREAK)
            val observation = change.observation("minecraft:air")

            assertIs<DamageReportUnavailable>(
                fixture.reports.generate(
                    GenerateDamageReport(fixture.battle.id, listOf(observation)),
                ).rejection(),
            )
            assertIs<DuplicateFinalBlockObservations>(
                fixture.reports.generate(
                    GenerateDamageReport(fixture.battle.id, listOf(observation, observation)),
                ).rejection(),
            )

            fixture.wars.beginResolution(fixture.battle.id, force = true).appliedValue()
            assertIs<DamageReportObservationMismatch>(
                fixture.reports.generate(
                    GenerateDamageReport(fixture.battle.id, emptyList()),
                ).rejection(),
            )
            assertIs<DamageReportObservationMismatch>(
                fixture.reports.generate(
                    GenerateDamageReport(
                        fixture.battle.id,
                        listOf(
                            observation,
                            FinalBlockObservation(
                                BlockChangeId(UUID(9, 999)),
                                SimpleBlockSnapshot("minecraft:air"),
                            ),
                        ),
                    ),
                ).rejection(),
            )
            assertNull(database.repository.read { findDamageReport(fixture.battle.id) })
            database.connectionFactory.open().use { connection ->
                connection.prepareStatement(
                    "SELECT COUNT(*) FROM battle_damage_report_entries WHERE battle_id = ?",
                ).use { statement ->
                    statement.setString(1, fixture.battle.id.toString())
                    statement.executeQuery().use { results ->
                        results.next()
                        assertEquals(0, results.getInt(1))
                    }
                }
            }
        }
    }

    @Test
    fun `seals an empty report for a battle without journaled changes`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.wars.beginResolution(fixture.battle.id, force = true).appliedValue()

            val report = fixture.reports.generate(
                GenerateDamageReport(fixture.battle.id, emptyList()),
            ).appliedValue()

            assertEquals(0, report.journaledChangeCount)
            assertEquals(0, report.baseRepairUnitCount)
            assertEquals(report, database.repository.read { findDamageReport(fixture.battle.id) })
        }
    }

    private fun fixture(database: SqliteTestDatabase): Fixture {
        database.migrator.migrate()
        val clock = MutableClock(Instant.parse("2026-08-18T12:00:00Z"))
        val ids = SequentialIdGenerator()
        val seasons = SeasonService(database.repository, ids, clock)
        val season = seasons.create("Season One").appliedValue()
        val civilizations = CivilizationService(database.repository, ids, clock)
        val north = civilizations.provision(
            ProvisionCivilization(
                season.id,
                "North",
                playerId(1),
                memberIds = setOf(playerId(2)),
            ),
        ).appliedValue().civilization
        val south = civilizations.provision(
            ProvisionCivilization(
                season.id,
                "South",
                playerId(3),
                memberIds = setOf(playerId(4)),
            ),
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
        seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
        seasons.transition(season.id, SeasonStatus.WAR).appliedValue()
        val wars = WarService(database.repository, ids, clock)
        val war = wars.declare(
            DeclareWar(season.id, north.id, south.id, playerId(1), 60),
        ).appliedValue()
        wars.activate(war.id).appliedValue()
        val battle = wars.startBattleFromEntry(war.id, playerId(2), southClaim.id)
            .appliedValue().battle
        return Fixture(
            clock,
            battle,
            southClaim,
            wars,
            DamageJournalService(database.repository, ids, clock),
            DamageReportService(database.repository, clock),
        )
    }

    private inner class Fixture(
        val clock: MutableClock,
        val battle: Battle,
        val southClaim: Claim,
        val wars: WarService,
        val journal: DamageJournalService,
        val reports: DamageReportService,
    ) {
        fun prepare(x: Int, state: String, cause: BlockMutationCause): BattleBlockChange =
            journal.prepare(
                PrepareBlockMutation(
                    battle.id,
                    southClaim.id,
                    BlockPosition3D(world, x, 72, 8),
                    SimpleBlockSnapshot(state),
                    playerId(2),
                    cause,
                ),
            ).appliedValue().journalEntry
    }

    private fun BattleBlockChange.observation(state: String) = FinalBlockObservation(
        id,
        SimpleBlockSnapshot(state),
    )

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }
}
