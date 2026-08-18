package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.claim.ClaimSpatialIndex
import io.bennyc.civilizations.application.persistence.PersistenceRecordNotFoundException
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcCivilizationsRepositoryTest {
    private val instant = Instant.parse("2026-08-18T12:00:00Z")

    @Test
    fun `round trips seasons civilizations memberships and claims`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val season = season(1, "Season One")
            val civilization = civilization(1, season.id, "Builder's Union")
            val leader = membership(season.id, civilization.id, playerId(1), MembershipRole.LEADER)
            val claim = claim(1, season.id, civilization.id, -33, -17, 48, 31)

            database.repository.transaction {
                insertSeason(season)
                insertCivilization(civilization)
                insertMembership(leader)
                insertClaim(claim)
            }

            database.repository.read {
                assertEquals(season, findSeason(season.id))
                assertEquals(listOf(season), listSeasons())
                assertEquals(civilization, findCivilization(civilization.id))
                assertEquals(
                    civilization,
                    findCivilizationByName(
                        season.id,
                        CivilizationName.from("  BUILDER'S UNION  "),
                    ),
                )
                assertEquals(listOf(civilization), listCivilizations(season.id))
                assertEquals(leader, findMembership(season.id, leader.playerId))
                assertEquals(listOf(leader), listMemberships(civilization.id))
                assertEquals(claim, findClaim(claim.id))
                assertEquals(listOf(claim), listClaims(civilization.id))
                assertEquals(listOf(claim), listClaimsForSeason(season.id))

                val index = ClaimSpatialIndex(season.id, listClaimsForSeason(season.id))
                assertEquals(
                    claim,
                    index.claimAt(BlockPosition2D(claim.bounds.worldId, -1, -1)),
                )
            }
        }
    }

    @Test
    fun `database constraints enforce season membership and leadership invariants`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val firstSeason = season(1, "Season One")
            val secondSeason = season(2, "Season Two")
            val firstCiv = civilization(1, firstSeason.id, "North")
            val secondCiv = civilization(2, firstSeason.id, "South")
            val futureCiv = civilization(3, secondSeason.id, "North")
            val firstLeader = membership(
                firstSeason.id,
                firstCiv.id,
                playerId(1),
                MembershipRole.LEADER,
            )

            database.repository.transaction {
                insertSeason(firstSeason)
                insertSeason(secondSeason)
                insertCivilization(firstCiv)
                insertCivilization(secondCiv)
                insertCivilization(futureCiv)
                insertMembership(firstLeader)
            }

            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertCivilization(civilization(4, firstSeason.id, "NORTH"))
                }
            }
            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertMembership(
                        membership(
                            firstSeason.id,
                            secondCiv.id,
                            firstLeader.playerId,
                            MembershipRole.MEMBER,
                        ),
                    )
                }
            }
            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertMembership(
                        membership(
                            firstSeason.id,
                            firstCiv.id,
                            playerId(2),
                            MembershipRole.LEADER,
                        ),
                    )
                }
            }
            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertClaim(claim(9, firstSeason.id, futureCiv.id, 0, 0, 4, 4))
                }
            }

            val futureMembership = membership(
                secondSeason.id,
                futureCiv.id,
                firstLeader.playerId,
                MembershipRole.LEADER,
            )
            database.repository.transaction {
                insertMembership(futureMembership)
            }

            database.repository.read {
                assertEquals(firstLeader, findMembership(firstSeason.id, firstLeader.playerId))
                assertEquals(futureMembership, findMembership(secondSeason.id, firstLeader.playerId))
                assertEquals(futureCiv, findCivilizationByName(secondSeason.id, firstCiv.name))
            }
        }
    }

    @Test
    fun `failed transaction rolls back all earlier writes`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val season = season(1, "Season One")
            val civilization = civilization(1, season.id, "Rollback Republic")
            database.repository.transaction { insertSeason(season) }

            assertFailsWith<SQLException> {
                database.repository.transaction {
                    insertCivilization(civilization)
                    insertCivilization(civilization.copy(name = CivilizationName.from("Duplicate ID")))
                }
            }

            database.repository.read {
                assertNull(findCivilization(civilization.id))
                assertTrue(listCivilizations(season.id).isEmpty())
            }
        }
    }

    @Test
    fun `leadership transfer is an atomic pair of membership updates`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val season = season(1, "Season One")
            val civilization = civilization(1, season.id, "Succession")
            val oldLeader = membership(
                season.id,
                civilization.id,
                playerId(1),
                MembershipRole.LEADER,
            )
            val successor = membership(
                season.id,
                civilization.id,
                playerId(2),
                MembershipRole.MEMBER,
            )
            database.repository.transaction {
                insertSeason(season)
                insertCivilization(civilization)
                insertMembership(oldLeader)
                insertMembership(successor)
            }

            val demoted = oldLeader.copy(role = MembershipRole.MEMBER)
            val promoted = successor.copy(role = MembershipRole.LEADER)
            database.repository.transaction {
                updateMembership(demoted)
                updateMembership(promoted)
            }

            database.repository.read {
                assertEquals(
                    setOf(demoted, promoted),
                    listMemberships(civilization.id).toSet(),
                )
            }
        }
    }

    @Test
    fun `membership moves between civilizations without duplicate state`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val season = season(1, "Season One")
            val origin = civilization(1, season.id, "Origin")
            val destination = civilization(2, season.id, "Destination")
            val originalMembership = membership(
                season.id,
                origin.id,
                playerId(1),
                MembershipRole.MEMBER,
            )
            database.repository.transaction {
                insertSeason(season)
                insertCivilization(origin)
                insertCivilization(destination)
                insertMembership(originalMembership)
            }

            val movedMembership = originalMembership.copy(civilizationId = destination.id)
            database.repository.transaction {
                updateMembership(movedMembership)
            }

            database.repository.read {
                assertEquals(movedMembership, findMembership(season.id, originalMembership.playerId))
                assertTrue(listMemberships(origin.id).isEmpty())
                assertEquals(listOf(movedMembership), listMemberships(destination.id))
            }
        }
    }

    @Test
    fun `updates deletes and missing record failures have explicit behavior`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            val season = season(1, "Season One")
            val civilization = civilization(1, season.id, "Mutable Draft")
            val member = membership(
                season.id,
                civilization.id,
                playerId(1),
                MembershipRole.LEADER,
            )
            val claim = claim(1, season.id, civilization.id, 0, 0, 15, 15)
            database.repository.transaction {
                insertSeason(season)
                insertCivilization(civilization)
                insertMembership(member)
                insertClaim(claim)
            }

            val openedSeason = season.copy(
                status = SeasonStatus.PEACE,
                updatedAt = instant.plusSeconds(1),
            )
            val activated = civilization.copy(
                status = CivilizationStatus.ACTIVE,
                updatedAt = instant.plusSeconds(1),
            )
            database.repository.transaction {
                updateSeason(openedSeason)
                updateCivilization(activated)
                assertTrue(deleteMembership(season.id, member.playerId))
                assertFalse(deleteMembership(season.id, member.playerId))
                assertTrue(deleteClaim(claim.id))
                assertFalse(deleteClaim(claim.id))
            }

            database.repository.read {
                assertEquals(openedSeason, findSeason(season.id))
                assertEquals(activated, findCivilization(civilization.id))
                assertNull(findMembership(season.id, member.playerId))
                assertNull(findClaim(claim.id))
            }

            assertFailsWith<PersistenceRecordNotFoundException> {
                database.repository.transaction {
                    updateCivilization(civilization(99, season.id, "Missing"))
                }
            }
        }
    }

    private fun season(id: Int, name: String): Season = Season(
        id = SeasonId(UUID(10L, id.toLong())),
        name = name,
        status = SeasonStatus.SETUP,
        createdAt = instant,
        updatedAt = instant,
    )

    private fun civilization(
        id: Int,
        seasonId: SeasonId,
        name: String,
    ): Civilization = Civilization(
        id = CivilizationId(UUID(20L, id.toLong())),
        seasonId = seasonId,
        name = CivilizationName.from(name),
        status = CivilizationStatus.DRAFT,
        createdAt = instant,
        updatedAt = instant,
    )

    private fun membership(
        seasonId: SeasonId,
        civilizationId: CivilizationId,
        playerId: PlayerId,
        role: MembershipRole,
    ): Membership = Membership(
        seasonId = seasonId,
        civilizationId = civilizationId,
        playerId = playerId,
        role = role,
        joinedAt = instant,
    )

    private fun claim(
        id: Int,
        seasonId: SeasonId,
        civilizationId: CivilizationId,
        firstX: Int,
        firstZ: Int,
        secondX: Int,
        secondZ: Int,
    ): Claim = Claim(
        id = ClaimId(UUID(30L, id.toLong())),
        seasonId = seasonId,
        civilizationId = civilizationId,
        bounds = ClaimBounds.between(
            WorldId("minecraft:overworld"),
            firstX,
            firstZ,
            secondX,
            secondZ,
        ),
    )

    private fun playerId(id: Int): PlayerId = PlayerId(UUID(40L, id.toLong()))
}
