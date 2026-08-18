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
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DamageJournalServiceTest {
    private val world = WorldId("minecraft:overworld")

    @Test
    fun `first mutation preserves the original state across later enemy and owner changes`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val position = BlockPosition3D(world, 40, 72, 8)
            val first = fixture.journal.prepare(
                fixture.request(
                    position = position,
                    state = "minecraft:stone",
                    actor = 2,
                    cause = BlockMutationCause.PLAYER_BREAK,
                ),
            ).appliedValue()
            assertTrue(first.capturedOriginalState)
            assertEquals(JournalActorRelationship.OPPONENT, first.relationship)

            val repeated = fixture.journal.prepare(
                fixture.request(
                    position = position,
                    state = "minecraft:cobblestone",
                    actor = 3,
                    cause = BlockMutationCause.EXPLOSION,
                ),
            ).unchangedValue()
            assertEquals(JournalActorRelationship.OWNER, repeated.relationship)
            assertEquals(SimpleBlockSnapshot("minecraft:cobblestone"), repeated.expectedCurrentState)
            assertEquals(SimpleBlockSnapshot("minecraft:stone"), repeated.journalEntry.originalState)
            assertEquals(playerId(2), repeated.journalEntry.firstActorId)
            assertEquals(BlockMutationCause.PLAYER_BREAK, repeated.journalEntry.firstMutationCause)
            assertEquals(1, database.repository.read { countBlockChanges(fixture.battle.id) })

            val afterRestart = DamageJournalService(
                database.repository,
                SequentialIdGenerator(),
                fixture.clock,
            )
            val persisted = database.repository.read {
                findBlockChange(fixture.battle.id, position)
            }
            assertEquals(first.journalEntry, persisted)
            assertEquals(
                first.journalEntry,
                afterRestart.prepare(
                    fixture.request(
                        position = position,
                        state = "minecraft:air",
                        actor = 2,
                        cause = BlockMutationCause.PLAYER_PLACE,
                    ),
                ).unchangedValue().journalEntry,
            )
        }
    }

    @Test
    fun `air is journaled so battle placements can be removed during reconstruction`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val prepared = fixture.journal.prepare(
                fixture.request(
                    position = BlockPosition3D(world, 41, 73, 8),
                    state = "minecraft:air",
                    actor = 1,
                    cause = BlockMutationCause.PLAYER_PLACE,
                ),
            ).appliedValue()

            assertEquals(SimpleBlockSnapshot("minecraft:air"), prepared.journalEntry.originalState)
            assertEquals(BlockMutationCause.PLAYER_PLACE, prepared.journalEntry.firstMutationCause)
        }
    }

    @Test
    fun `journal fails closed for outsiders wrong land peace and expired windows`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val position = BlockPosition3D(world, 40, 72, 8)

            assertIs<ActorNotInBattleJournal>(
                fixture.journal.prepare(fixture.request(position, actor = 99)).rejection(),
            )
            assertIs<PositionOutsideBattleLand>(
                fixture.journal.prepare(
                    fixture.request(
                        position = BlockPosition3D(world, 80, 72, 8),
                        claim = fixture.southClaim,
                    ),
                ).rejection(),
            )

            fixture.seasons.transition(fixture.seasonId, SeasonStatus.PEACE).appliedValue()
            assertIs<BattleJournalPhaseClosed>(
                fixture.journal.prepare(fixture.request(position)).rejection(),
            )
            fixture.seasons.transition(fixture.seasonId, SeasonStatus.WAR).appliedValue()
            fixture.clock.advanceSeconds(60)
            assertIs<BattleJournalOutsideWindow>(
                fixture.journal.prepare(fixture.request(position)).rejection(),
            )
            assertEquals(0, database.repository.read { countBlockChanges(fixture.battle.id) })
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
            clock = clock,
            seasons = seasons,
            seasonId = season.id,
            battle = battle,
            southClaim = southClaim,
            journal = DamageJournalService(database.repository, ids, clock),
        )
    }

    private data class Fixture(
        val clock: MutableClock,
        val seasons: SeasonService,
        val seasonId: io.bennyc.civilizations.domain.identity.SeasonId,
        val battle: Battle,
        val southClaim: Claim,
        val journal: DamageJournalService,
    ) {
        fun request(
            position: BlockPosition3D,
            state: String = "minecraft:stone",
            actor: Long = 2,
            cause: BlockMutationCause = BlockMutationCause.PLAYER_BREAK,
            claim: Claim = southClaim,
        ) = PrepareBlockMutation(
            battleId = battle.id,
            claimId = claim.id,
            position = position,
            observedState = SimpleBlockSnapshot(state),
            actorId = playerId(actor),
            cause = cause,
        )
    }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }
}
