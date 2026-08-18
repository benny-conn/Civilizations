package io.bennyc.civilizations.application.season

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.application.support.unchangedValue
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SeasonServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `creates normalized setup season and rejects duplicate name`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val service = SeasonService(database.repository, SequentialIdGenerator(), clock)

            val season = service.create("  Season One  ").appliedValue()

            assertEquals("Season One", season.name)
            assertEquals(SeasonStatus.SETUP, season.status)
            assertIs<SeasonNameAlreadyExists>(service.create("season one").rejection())
            database.repository.read {
                assertEquals(listOf(season), listSeasons())
            }
        }
    }

    @Test
    fun `war is an explicit reversible gate and archive is terminal`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val service = SeasonService(database.repository, SequentialIdGenerator(), clock)
            val season = service.create("Season One").appliedValue()

            assertIs<InvalidSeasonTransition>(
                service.transition(season.id, SeasonStatus.WAR).rejection(),
            )
            val peace = service.transition(season.id, SeasonStatus.PEACE).appliedValue()
            val war = service.transition(season.id, SeasonStatus.WAR).appliedValue()
            val emergencyPeace = service.transition(season.id, SeasonStatus.PEACE).appliedValue()
            assertEquals(SeasonStatus.PEACE, peace.status)
            assertEquals(SeasonStatus.WAR, war.status)
            assertEquals(SeasonStatus.PEACE, emergencyPeace.status)
            assertEquals(
                emergencyPeace,
                service.transition(season.id, SeasonStatus.PEACE).unchangedValue(),
            )

            val archived = service.transition(season.id, SeasonStatus.ARCHIVED).appliedValue()
            assertEquals(SeasonStatus.ARCHIVED, archived.status)
            assertIs<InvalidSeasonTransition>(
                service.transition(season.id, SeasonStatus.PEACE).rejection(),
            )
        }
    }

    @Test
    fun `invalid name returns a command-safe rejection`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val service = SeasonService(database.repository, SequentialIdGenerator(), clock)

            val result = service.create("   ")

            assertIs<ApplicationResult.Rejected>(result)
            assertIs<InvalidSeasonName>(result.failure)
        }
    }
}
