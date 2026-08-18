package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleParticipant
import io.bennyc.civilizations.domain.war.BattleSide
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.War
import io.bennyc.civilizations.domain.war.WarId
import io.bennyc.civilizations.domain.war.WarRulesSnapshot
import io.bennyc.civilizations.domain.war.WarStatus
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WarPersistenceTest {
    private val now = Instant.parse("2026-08-18T12:00:00Z")

    @Test
    fun `round trips a war battle and immutable participant snapshot`() {
        SqliteTestDatabase().use { database ->
            val seed = seed(database)
            val declared = seed.war()
            val active = declared.copy(
                status = WarStatus.ACTIVE,
                activatedAt = now.plusSeconds(1),
                updatedAt = now.plusSeconds(1),
            )
            val battle = seed.battle(active)
            val participants = listOf(
                seed.participant(battle, seed.northLeader, BattleSide.ATTACKER),
                seed.participant(battle, seed.southLeader, BattleSide.DEFENDER),
            )

            database.repository.transaction {
                insertWar(declared)
                updateWar(active)
                insertBattle(battle)
                participants.forEach(::insertBattleParticipant)
            }

            database.repository.read {
                assertEquals(active, findWar(active.id))
                assertEquals(listOf(active), listWarsForSeason(seed.season.id))
                assertEquals(listOf(active), listOpenWarsForCivilization(seed.north.id))
                assertEquals(battle, findBattle(battle.id))
                assertEquals(listOf(battle), listBattlesForWar(active.id))
                assertEquals(participants.toSet(), listBattleParticipants(battle.id).toSet())
            }
        }
    }

    @Test
    fun `database rejects invalid war entry battle entry roster and premature war end`() {
        SqliteTestDatabase().use { database ->
            val seed = seed(database)

            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertWar(seed.war(declaredBy = seed.northMember))
                }
            }

            val active = seed.war().copy(
                status = WarStatus.ACTIVE,
                activatedAt = now.plusSeconds(1),
                updatedAt = now.plusSeconds(1),
            )
            database.repository.transaction {
                insertWar(seed.war())
                updateWar(active)
            }
            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertBattle(seed.battle(active, triggerClaim = seed.northClaim))
                }
            }

            val battle = seed.battle(active)
            database.repository.transaction { insertBattle(battle) }
            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertBattleParticipant(
                        seed.participant(battle, seed.northMember, BattleSide.DEFENDER),
                    )
                }
            }
            assertFailsWith<SQLException> {
                database.repository.transaction {
                    updateWar(
                        active.copy(
                            status = WarStatus.CLOSED,
                            endedAt = now.plusSeconds(2),
                            updatedAt = now.plusSeconds(2),
                        ),
                    )
                }
            }
        }
    }

    private fun seed(database: SqliteTestDatabase): Seed {
        database.migrator.migrate()
        val season = Season(
            id = SeasonId(id(1)),
            name = "Season One",
            status = SeasonStatus.WAR,
            createdAt = now,
            updatedAt = now,
        )
        val north = civilization(10, season.id, "North")
        val south = civilization(11, season.id, "South")
        val northLeader = membership(20, season.id, north.id, MembershipRole.LEADER)
        val northMember = membership(21, season.id, north.id, MembershipRole.MEMBER)
        val southLeader = membership(22, season.id, south.id, MembershipRole.LEADER)
        val northClaim = claim(30, season.id, north.id, 0)
        val southClaim = claim(31, season.id, south.id, 32)
        database.repository.transaction {
            insertSeason(season)
            insertCivilization(north)
            insertCivilization(south)
            insertMembership(northLeader)
            insertMembership(northMember)
            insertMembership(southLeader)
            insertClaim(northClaim)
            insertClaim(southClaim)
        }
        return Seed(
            season,
            north,
            south,
            northLeader,
            northMember,
            southLeader,
            northClaim,
            southClaim,
        )
    }

    private fun civilization(id: Long, seasonId: SeasonId, name: String) = Civilization(
        id = CivilizationId(id(id)),
        seasonId = seasonId,
        name = CivilizationName.from(name),
        status = CivilizationStatus.ACTIVE,
        createdAt = now,
        updatedAt = now,
    )

    private fun membership(
        id: Long,
        seasonId: SeasonId,
        civilizationId: CivilizationId,
        role: MembershipRole,
    ) = Membership(
        seasonId = seasonId,
        civilizationId = civilizationId,
        playerId = PlayerId(id(id)),
        role = role,
        joinedAt = now,
    )

    private fun claim(id: Long, seasonId: SeasonId, civilizationId: CivilizationId, x: Int) =
        Claim(
            id = ClaimId(id(id)),
            seasonId = seasonId,
            civilizationId = civilizationId,
            bounds = ClaimBounds.between(
                WorldId("minecraft:overworld"),
                x,
                0,
                x + 15,
                15,
            ),
        )

    private fun id(value: Long) = UUID(9, value)

    private inner class Seed(
        val season: Season,
        val north: Civilization,
        val south: Civilization,
        val northLeader: Membership,
        val northMember: Membership,
        val southLeader: Membership,
        val northClaim: Claim,
        val southClaim: Claim,
    ) {
        fun war(declaredBy: Membership = northLeader) = War(
            id = WarId(id(40)),
            seasonId = season.id,
            declaringCivilizationId = north.id,
            targetCivilizationId = south.id,
            declaredByPlayerId = declaredBy.playerId,
            status = WarStatus.DECLARED,
            rules = WarRulesSnapshot(battleDurationSeconds = 60),
            declaredAt = now,
            activatedAt = null,
            endedAt = null,
            updatedAt = now,
        )

        fun battle(
            war: War,
            triggerClaim: Claim = southClaim,
        ) = Battle(
            id = BattleId(id(50)),
            warId = war.id,
            seasonId = season.id,
            attackingCivilizationId = north.id,
            defendingCivilizationId = south.id,
            triggeredByPlayerId = northMember.playerId,
            triggerClaimId = triggerClaim.id,
            status = BattleStatus.ACTIVE,
            startedAt = now.plusSeconds(1),
            endsAt = now.plusSeconds(61),
            resolvingAt = null,
            endedAt = null,
            outcome = null,
            winnerCivilizationId = null,
            updatedAt = now.plusSeconds(1),
        )

        fun participant(
            battle: Battle,
            membership: Membership,
            side: BattleSide,
        ) = BattleParticipant(
            seasonId = season.id,
            battleId = battle.id,
            playerId = membership.playerId,
            civilizationId = membership.civilizationId,
            side = side,
            joinedAt = battle.startedAt,
        )
    }
}
