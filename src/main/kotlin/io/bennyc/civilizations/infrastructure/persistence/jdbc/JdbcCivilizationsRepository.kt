package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.persistence.CivilizationsReadContext
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
import io.bennyc.civilizations.application.persistence.PersistenceRecordNotFoundException
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
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

class JdbcCivilizationsRepository(
    private val connectionFactory: JdbcConnectionFactory,
) : CivilizationsRepository {
    override fun <T> read(block: CivilizationsReadContext.() -> T): T =
        connectionFactory.open().use { connection ->
            JdbcReadContext(connection).block()
        }

    override fun <T> transaction(block: CivilizationsWriteContext.() -> T): T =
        connectionFactory.open().use { connection ->
            connection.autoCommit = false
            try {
                val result = JdbcWriteContext(connection).block()
                connection.commit()
                result
            } catch (failure: Throwable) {
                connection.rollbackAfter(failure)
                throw failure
            }
        }
}

private open class JdbcReadContext(
    protected val connection: Connection,
) : CivilizationsReadContext {
    override fun findSeason(id: SeasonId): Season? = queryOne(
        sql = """
            SELECT id, name, status, created_at_ms, updated_at_ms
            FROM seasons
            WHERE id = ?
        """.trimIndent(),
        bind = { setString(1, id.toString()) },
        map = ResultSet::toSeason,
    )

    override fun listSeasons(): List<Season> = queryMany(
        sql = """
            SELECT id, name, status, created_at_ms, updated_at_ms
            FROM seasons
            ORDER BY created_at_ms, id
        """.trimIndent(),
        map = ResultSet::toSeason,
    )

    override fun findCivilization(id: CivilizationId): Civilization? = queryOne(
        sql = "$CIVILIZATION_SELECT WHERE id = ?",
        bind = { setString(1, id.toString()) },
        map = ResultSet::toCivilization,
    )

    override fun findCivilizationByName(
        seasonId: SeasonId,
        name: CivilizationName,
    ): Civilization? = queryOne(
        sql = "$CIVILIZATION_SELECT WHERE season_id = ? AND normalized_name = ?",
        bind = {
            setString(1, seasonId.toString())
            setString(2, name.normalized)
        },
        map = ResultSet::toCivilization,
    )

    override fun listCivilizations(seasonId: SeasonId): List<Civilization> = queryMany(
        sql = "$CIVILIZATION_SELECT WHERE season_id = ? ORDER BY normalized_name, id",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toCivilization,
    )

    override fun findMembership(
        seasonId: SeasonId,
        playerId: PlayerId,
    ): Membership? = queryOne(
        sql = "$MEMBERSHIP_SELECT WHERE season_id = ? AND player_id = ?",
        bind = {
            setString(1, seasonId.toString())
            setString(2, playerId.toString())
        },
        map = ResultSet::toMembership,
    )

    override fun listMemberships(civilizationId: CivilizationId): List<Membership> = queryMany(
        sql = "$MEMBERSHIP_SELECT WHERE civilization_id = ? ORDER BY role, joined_at_ms, player_id",
        bind = { setString(1, civilizationId.toString()) },
        map = ResultSet::toMembership,
    )

    override fun findClaim(id: ClaimId): Claim? = queryOne(
        sql = "$CLAIM_SELECT WHERE id = ?",
        bind = { setString(1, id.toString()) },
        map = ResultSet::toClaim,
    )

    override fun listClaims(civilizationId: CivilizationId): List<Claim> = queryMany(
        sql = "$CLAIM_SELECT WHERE civilization_id = ? ORDER BY world_id, min_x, min_z, id",
        bind = { setString(1, civilizationId.toString()) },
        map = ResultSet::toClaim,
    )

    override fun listClaimsForSeason(seasonId: SeasonId): List<Claim> = queryMany(
        sql = "$CLAIM_SELECT WHERE season_id = ? ORDER BY world_id, min_x, min_z, id",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toClaim,
    )

    protected fun executeUpdate(
        sql: String,
        bind: PreparedStatement.() -> Unit,
    ): Int = connection.prepareStatement(sql).use { statement ->
        statement.bind()
        statement.executeUpdate()
    }

    private fun <T> queryOne(
        sql: String,
        bind: PreparedStatement.() -> Unit = {},
        map: ResultSet.() -> T,
    ): T? = connection.prepareStatement(sql).use { statement ->
        statement.bind()
        statement.executeQuery().use { results ->
            if (!results.next()) {
                return@use null
            }
            val result = results.map()
            check(!results.next()) { "Expected at most one result for query: $sql" }
            result
        }
    }

    private fun <T> queryMany(
        sql: String,
        bind: PreparedStatement.() -> Unit = {},
        map: ResultSet.() -> T,
    ): List<T> = connection.prepareStatement(sql).use { statement ->
        statement.bind()
        statement.executeQuery().use { results ->
            buildList {
                while (results.next()) {
                    add(results.map())
                }
            }
        }
    }

    private companion object {
        const val CIVILIZATION_SELECT = """
            SELECT id, season_id, name, normalized_name, status, created_at_ms, updated_at_ms
            FROM civilizations
        """
        const val MEMBERSHIP_SELECT = """
            SELECT season_id, civilization_id, player_id, role, joined_at_ms
            FROM memberships
        """
        const val CLAIM_SELECT = """
            SELECT id, season_id, civilization_id, world_id, min_x, max_x, min_z, max_z
            FROM claims
        """
    }
}

private class JdbcWriteContext(
    connection: Connection,
) : JdbcReadContext(connection), CivilizationsWriteContext {
    override fun insertSeason(season: Season) {
        executeUpdate(
            sql = """
                INSERT INTO seasons(id, name, status, created_at_ms, updated_at_ms)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, season.id.toString())
            setString(2, season.name)
            setString(3, season.status.name)
            setLong(4, season.createdAt.toEpochMilli())
            setLong(5, season.updatedAt.toEpochMilli())
        }
    }

    override fun updateSeason(season: Season) {
        val updated = executeUpdate(
            sql = """
                UPDATE seasons
                SET name = ?, status = ?, updated_at_ms = ?
                WHERE id = ?
            """.trimIndent(),
        ) {
            setString(1, season.name)
            setString(2, season.status.name)
            setLong(3, season.updatedAt.toEpochMilli())
            setString(4, season.id.toString())
        }
        requireUpdated(updated, "Season ${season.id}")
    }

    override fun insertCivilization(civilization: Civilization) {
        executeUpdate(
            sql = """
                INSERT INTO civilizations(
                    id, season_id, name, normalized_name, status, created_at_ms, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, civilization.id.toString())
            setString(2, civilization.seasonId.toString())
            setString(3, civilization.name.value)
            setString(4, civilization.name.normalized)
            setString(5, civilization.status.name)
            setLong(6, civilization.createdAt.toEpochMilli())
            setLong(7, civilization.updatedAt.toEpochMilli())
        }
    }

    override fun updateCivilization(civilization: Civilization) {
        val updated = executeUpdate(
            sql = """
                UPDATE civilizations
                SET name = ?, normalized_name = ?, status = ?, updated_at_ms = ?
                WHERE id = ? AND season_id = ?
            """.trimIndent(),
        ) {
            setString(1, civilization.name.value)
            setString(2, civilization.name.normalized)
            setString(3, civilization.status.name)
            setLong(4, civilization.updatedAt.toEpochMilli())
            setString(5, civilization.id.toString())
            setString(6, civilization.seasonId.toString())
        }
        requireUpdated(updated, "Civilization ${civilization.id}")
    }

    override fun insertMembership(membership: Membership) {
        executeUpdate(
            sql = """
                INSERT INTO memberships(
                    season_id, civilization_id, player_id, role, joined_at_ms
                ) VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, membership.seasonId.toString())
            setString(2, membership.civilizationId.toString())
            setString(3, membership.playerId.toString())
            setString(4, membership.role.name)
            setLong(5, membership.joinedAt.toEpochMilli())
        }
    }

    override fun updateMembership(membership: Membership) {
        val updated = executeUpdate(
            sql = """
                UPDATE memberships
                SET civilization_id = ?, role = ?, joined_at_ms = ?
                WHERE season_id = ? AND player_id = ?
            """.trimIndent(),
        ) {
            setString(1, membership.civilizationId.toString())
            setString(2, membership.role.name)
            setLong(3, membership.joinedAt.toEpochMilli())
            setString(4, membership.seasonId.toString())
            setString(5, membership.playerId.toString())
        }
        requireUpdated(updated, "Membership ${membership.seasonId}/${membership.playerId}")
    }

    override fun deleteMembership(seasonId: SeasonId, playerId: PlayerId): Boolean =
        executeUpdate(
            sql = "DELETE FROM memberships WHERE season_id = ? AND player_id = ?",
        ) {
            setString(1, seasonId.toString())
            setString(2, playerId.toString())
        } > 0

    override fun insertClaim(claim: Claim) {
        executeUpdate(
            sql = """
                INSERT INTO claims(
                    id, season_id, civilization_id, world_id, min_x, max_x, min_z, max_z
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, claim.id.toString())
            setString(2, claim.seasonId.toString())
            setString(3, claim.civilizationId.toString())
            setString(4, claim.bounds.worldId.value)
            setInt(5, claim.bounds.minX)
            setInt(6, claim.bounds.maxX)
            setInt(7, claim.bounds.minZ)
            setInt(8, claim.bounds.maxZ)
        }
    }

    override fun deleteClaim(id: ClaimId): Boolean =
        executeUpdate(
            sql = "DELETE FROM claims WHERE id = ?",
        ) {
            setString(1, id.toString())
        } > 0

    private fun requireUpdated(updated: Int, description: String) {
        if (updated != 1) {
            throw PersistenceRecordNotFoundException("$description does not exist")
        }
    }
}

private fun ResultSet.toSeason(): Season = Season(
    id = SeasonId(uuid("id")),
    name = getString("name"),
    status = SeasonStatus.valueOf(getString("status")),
    createdAt = instant("created_at_ms"),
    updatedAt = instant("updated_at_ms"),
)

private fun ResultSet.toCivilization(): Civilization {
    val name = CivilizationName.from(getString("name"))
    check(name.normalized == getString("normalized_name")) {
        "Civilization normalized name does not match its display name"
    }
    return Civilization(
        id = CivilizationId(uuid("id")),
        seasonId = SeasonId(uuid("season_id")),
        name = name,
        status = CivilizationStatus.valueOf(getString("status")),
        createdAt = instant("created_at_ms"),
        updatedAt = instant("updated_at_ms"),
    )
}

private fun ResultSet.toMembership(): Membership = Membership(
    seasonId = SeasonId(uuid("season_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    playerId = PlayerId(uuid("player_id")),
    role = MembershipRole.valueOf(getString("role")),
    joinedAt = instant("joined_at_ms"),
)

private fun ResultSet.toClaim(): Claim = Claim(
    id = ClaimId(uuid("id")),
    seasonId = SeasonId(uuid("season_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    bounds = ClaimBounds.between(
        worldId = WorldId(getString("world_id")),
        firstX = getInt("min_x"),
        firstZ = getInt("min_z"),
        secondX = getInt("max_x"),
        secondZ = getInt("max_z"),
    ),
)

private fun ResultSet.uuid(column: String): UUID = UUID.fromString(getString(column))

private fun ResultSet.instant(column: String): Instant = Instant.ofEpochMilli(getLong(column))

private fun Connection.rollbackAfter(failure: Throwable) {
    try {
        rollback()
    } catch (rollbackFailure: Throwable) {
        failure.addSuppressed(rollbackFailure)
    }
}
