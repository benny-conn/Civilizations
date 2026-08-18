package io.bennyc.civilizations.application.war

import io.bennyc.civilizations.application.ApplicationResult
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
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleSide
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.War
import io.bennyc.civilizations.domain.war.WarStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarServiceTest {
    private val world = WorldId("minecraft:overworld")

    @Test
    fun `declaration requires a leader prevents duplicate pairs and allows multiple fronts`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)

            assertIs<WarDeclarerMustBeLeader>(
                fixture.wars.declare(fixture.declaration(declaredBy = 2)).rejection(),
            )

            val declared = fixture.wars.declare(fixture.declaration()).appliedValue()
            assertEquals(WarStatus.DECLARED, declared.status)
            assertEquals(declared, fixture.wars.declare(fixture.declaration()).unchangedValue())

            val secondFront = fixture.wars.declare(
                fixture.declaration(
                    declaringCivilizationId = fixture.westId,
                    targetCivilizationId = fixture.northId,
                    declaredBy = 5,
                ),
            ).appliedValue()
            assertIs<WarPairAlreadyOpen>(
                fixture.wars.declare(
                    fixture.declaration(
                        declaringCivilizationId = fixture.southId,
                        targetCivilizationId = fixture.northId,
                        declaredBy = 3,
                    ),
                ).rejection(),
            )
            database.repository.read {
                assertEquals(declared, findWar(declared.id))
                assertEquals(listOf(declared), listOpenWarsForCivilization(fixture.southId))
                assertEquals(
                    setOf(declared, secondFront),
                    listOpenWarsForCivilization(fixture.northId).toSet(),
                )
            }
        }
    }

    @Test
    fun `hostile claim entry assigns direction and snapshots both rosters`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val war = fixture.activeWar()

            assertIs<EntryIsNotOpponentLand>(
                fixture.wars.startBattleFromEntry(
                    war.id,
                    playerId(2),
                    fixture.northClaim.id,
                ).rejection(),
            )

            val roster = fixture.wars.startBattleFromEntry(
                war.id,
                playerId(2),
                fixture.southClaim.id,
            ).appliedValue()

            assertEquals(fixture.northId, roster.battle.attackingCivilizationId)
            assertEquals(fixture.southId, roster.battle.defendingCivilizationId)
            assertEquals(fixture.southClaim.id, roster.battle.triggerClaimId)
            assertEquals(Instant.parse("2026-08-18T12:10:00Z"), roster.battle.endsAt)
            assertEquals(
                setOf(playerId(1), playerId(2)),
                roster.participants
                    .filter { it.side == BattleSide.ATTACKER }
                    .mapTo(mutableSetOf()) { it.playerId },
            )
            assertEquals(
                setOf(playerId(3), playerId(4)),
                roster.participants
                    .filter { it.side == BattleSide.DEFENDER }
                    .mapTo(mutableSetOf()) { it.playerId },
            )

            val persisted = database.repository.read {
                findBattle(roster.battle.id) to listBattleParticipants(roster.battle.id)
            }
            assertEquals(roster.battle, persisted.first)
            assertEquals(roster.participants.toSet(), persisted.second.toSet())
        }
    }

    @Test
    fun `multiple wars may stay open but a civilization fights one battle at a time`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val northSouthWar = fixture.activeWar()
            val firstBattle = fixture.wars.startBattleFromEntry(
                northSouthWar.id,
                playerId(2),
                fixture.southClaim.id,
            ).appliedValue().battle
            val westNorthWar = fixture.wars.declare(
                fixture.declaration(
                    declaringCivilizationId = fixture.westId,
                    targetCivilizationId = fixture.northId,
                    declaredBy = 5,
                ),
            ).appliedValue().let { fixture.wars.activate(it.id).appliedValue() }

            assertIs<CivilizationAlreadyInOpenBattle>(
                fixture.wars.startBattleFromEntry(
                    westNorthWar.id,
                    playerId(5),
                    fixture.northClaim.id,
                ).rejection(),
            )

            fixture.wars.cancelBattle(firstBattle.id).appliedValue()
            val secondBattle = fixture.wars.startBattleFromEntry(
                westNorthWar.id,
                playerId(5),
                fixture.northClaim.id,
            ).appliedValue().battle
            assertEquals(fixture.westId, secondBattle.attackingCivilizationId)
            assertEquals(fixture.northId, secondBattle.defendingCivilizationId)
        }
    }

    @Test
    fun `expired battles recover deterministically and terminal transitions persist`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, battleDurationSeconds = 60)
            val war = fixture.activeWar()
            val battle = fixture.wars.startBattleFromEntry(
                war.id,
                playerId(4),
                fixture.northClaim.id,
            ).appliedValue().battle

            assertIs<BattleHasNotExpired>(fixture.wars.beginResolution(battle.id).rejection())
            fixture.clock.advanceSeconds(60)

            val recovered = fixture.wars.recoverExpiredBattles(fixture.seasonId).single()
            assertEquals(BattleStatus.RESOLVING, recovered.status)
            assertEquals(battle.endsAt, recovered.resolvingAt)
            assertTrue(fixture.wars.recoverExpiredBattles(fixture.seasonId).isEmpty())

            val closedBattle = fixture.wars.resolve(
                battle.id,
                BattleOutcome.ATTACKER_VICTORY,
            ).appliedValue()
            assertEquals(fixture.southId, closedBattle.winnerCivilizationId)
            val closedWar = fixture.wars.closeWar(war.id).appliedValue()
            assertEquals(WarStatus.CLOSED, closedWar.status)

            database.repository.read {
                assertEquals(closedBattle, findBattle(battle.id))
                assertEquals(closedWar, findWar(war.id))
                assertTrue(listOpenWarsForCivilization(fixture.northId).isEmpty())
                assertNull(findActiveSeasonId()?.takeIf { it != fixture.seasonId })
            }
        }
    }

    private fun fixture(
        database: SqliteTestDatabase,
        battleDurationSeconds: Long = 600,
    ): Fixture {
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
        val west = civilizations.provision(
            ProvisionCivilization(season.id, "West", playerId(5)),
        ).appliedValue().civilization
        val claims = ClaimService(
            database.repository,
            ids,
            ClaimRules(maxArea = 256, maxClaimsPerCivilization = 4),
        )
        val northClaim = claims.place(
            PlaceClaim(
                north.id,
                ClaimBounds.between(world, 0, 0, 15, 15),
            ),
        ).appliedValue()
        val southClaim = claims.place(
            PlaceClaim(
                south.id,
                ClaimBounds.between(world, 32, 0, 47, 15),
            ),
        ).appliedValue()
        seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
        seasons.transition(season.id, SeasonStatus.WAR).appliedValue()
        return Fixture(
            clock = clock,
            wars = WarService(database.repository, ids, clock),
            seasonId = season.id,
            northId = north.id,
            southId = south.id,
            westId = west.id,
            northClaim = northClaim,
            southClaim = southClaim,
            battleDurationSeconds = battleDurationSeconds,
        )
    }

    private data class Fixture(
        val clock: MutableClock,
        val wars: WarService,
        val seasonId: SeasonId,
        val northId: CivilizationId,
        val southId: CivilizationId,
        val westId: CivilizationId,
        val northClaim: Claim,
        val southClaim: Claim,
        val battleDurationSeconds: Long,
    ) {
        fun declaration(
            declaringCivilizationId: CivilizationId = northId,
            targetCivilizationId: CivilizationId = southId,
            declaredBy: Long = 1,
        ): DeclareWar = DeclareWar(
            seasonId = seasonId,
            declaringCivilizationId = declaringCivilizationId,
            targetCivilizationId = targetCivilizationId,
            declaredByPlayerId = playerId(declaredBy),
            battleDurationSeconds = battleDurationSeconds,
        )

        fun activeWar(): War {
            val declared = wars.declare(declaration()).appliedValue()
            return wars.activate(declared.id).appliedValue()
        }
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
