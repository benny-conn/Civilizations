package io.bennyc.civilizations.application.civilization

import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.application.support.unchangedValue
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CivilizationServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `draft civilizations are valid without leaders members or land`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val ids = SequentialIdGenerator()
            val season = SeasonService(database.repository, ids, clock)
                .create("Season One")
                .appliedValue()
            val service = CivilizationService(database.repository, ids, clock)

            val draft = service.createDraft(season.id, "The Empty Quarter").appliedValue()

            assertEquals(CivilizationStatus.DRAFT, draft.status)
            assertIs<CivilizationHasNoLeader>(service.activate(draft.id).rejection())
            database.repository.read {
                assertTrue(listMemberships(draft.id).isEmpty())
                assertTrue(listClaims(draft.id).isEmpty())
            }
        }
    }

    @Test
    fun `provisioning creates an active landless offline roster and is idempotent`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val ids = SequentialIdGenerator()
            val season = SeasonService(database.repository, ids, clock)
                .create("Season One")
                .appliedValue()
            val service = CivilizationService(database.repository, ids, clock)
            val request = ProvisionCivilization(
                seasonId = season.id,
                rawName = "Builder's Union",
                leaderId = playerId(1),
                memberIds = setOf(playerId(2), playerId(3)),
            )

            val roster = service.provision(request).appliedValue()
            val repeated = service.provision(request).unchangedValue()

            assertEquals(CivilizationStatus.ACTIVE, roster.civilization.status)
            assertEquals(roster, repeated)
            assertEquals(3, roster.memberships.size)
            assertEquals(playerId(1), roster.memberships.single { it.role == MembershipRole.LEADER }.playerId)
            database.repository.read {
                assertTrue(listClaims(roster.civilization.id).isEmpty())
            }
        }
    }

    @Test
    fun `one player cannot be provisioned into two civilizations`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val ids = SequentialIdGenerator()
            val season = SeasonService(database.repository, ids, clock)
                .create("Season One")
                .appliedValue()
            val service = CivilizationService(database.repository, ids, clock)
            service.provision(
                ProvisionCivilization(season.id, "North", playerId(1), setOf(playerId(2))),
            ).appliedValue()

            assertIs<PlayerAlreadyAssigned>(
                service.provision(
                    ProvisionCivilization(season.id, "South", playerId(3), setOf(playerId(2))),
                ).rejection(),
            )
            database.repository.read {
                assertEquals(null, findCivilizationByName(season.id, io.bennyc.civilizations.domain.civilization.CivilizationName.from("South")))
                assertEquals(1, listCivilizations(season.id).size)
            }
        }
    }

    @Test
    fun `leadership transfer is atomic and leaders cannot move`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val ids = SequentialIdGenerator()
            val seasonService = SeasonService(database.repository, ids, clock)
            val season = seasonService.create("Season One").appliedValue()
            val service = CivilizationService(database.repository, ids, clock)
            val north = service.provision(
                ProvisionCivilization(season.id, "North", playerId(1), setOf(playerId(2))),
            ).appliedValue().civilization
            val south = service.provision(
                ProvisionCivilization(season.id, "South", playerId(3)),
            ).appliedValue().civilization

            assertIs<LeaderCannotMove>(
                service.moveMember(season.id, playerId(1), south.id).rejection(),
            )
            val transferred = service.transferLeadership(north.id, playerId(2)).appliedValue()
            assertEquals(playerId(2), transferred.memberships.single { it.role == MembershipRole.LEADER }.playerId)
            val moved = service.moveMember(season.id, playerId(1), south.id).appliedValue()
            assertEquals(south.id, moved.civilizationId)
            database.repository.read {
                assertEquals(playerId(2), listMemberships(north.id).single().playerId)
                assertEquals(2, listMemberships(south.id).size)
            }
        }
    }

    @Test
    fun `default roster gate allows war changes for future battle snapshots`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val ids = SequentialIdGenerator()
            val seasonService = SeasonService(database.repository, ids, clock)
            val season = seasonService.create("Season One").appliedValue()
            val service = CivilizationService(database.repository, ids, clock)
            val civilization = service.provision(
                ProvisionCivilization(season.id, "North", playerId(1)),
            ).appliedValue().civilization
            seasonService.transition(season.id, SeasonStatus.PEACE).appliedValue()
            seasonService.transition(season.id, SeasonStatus.WAR).appliedValue()

            val membership = service.assignMember(civilization.id, playerId(2)).appliedValue()
            assertEquals(civilization.id, membership.civilizationId)
        }
    }

    @Test
    fun `configured setup-only roster gate closes changes in peace`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val ids = SequentialIdGenerator()
            val seasonService = SeasonService(database.repository, ids, clock)
            val season = seasonService.create("Season One").appliedValue()
            val service = CivilizationService(
                database.repository,
                ids,
                clock,
                GameplayPhaseRules(rosterChangesAllowedIn = setOf(SeasonStatus.SETUP)),
            )
            val civilization = service.provision(
                ProvisionCivilization(season.id, "North", playerId(1)),
            ).appliedValue().civilization
            seasonService.transition(season.id, SeasonStatus.PEACE).appliedValue()

            assertIs<RosterChangesClosed>(
                service.assignMember(civilization.id, playerId(2)).rejection(),
            )
        }
    }
}
