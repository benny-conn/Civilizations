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
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleParticipant
import io.bennyc.civilizations.domain.war.BattleSide
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.BattleTrigger
import io.bennyc.civilizations.domain.war.LandDestructionScope
import io.bennyc.civilizations.domain.war.War
import io.bennyc.civilizations.domain.war.WarId
import io.bennyc.civilizations.domain.war.WarRulesSnapshot
import io.bennyc.civilizations.domain.war.WarStatus
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
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
    override fun findActiveSeasonId(): SeasonId? = queryOne(
        sql = "SELECT active_season_id FROM runtime_state WHERE singleton_id = 1",
        map = {
            getString("active_season_id")?.let { SeasonId(UUID.fromString(it)) }
        },
    )

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

    override fun findWar(id: WarId): War? = queryOne(
        sql = "$WAR_SELECT WHERE id = ?",
        bind = { setString(1, id.toString()) },
        map = ResultSet::toWar,
    )

    override fun listWarsForSeason(seasonId: SeasonId): List<War> = queryMany(
        sql = "$WAR_SELECT WHERE season_id = ? ORDER BY declared_at_ms, id",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toWar,
    )

    override fun listOpenWarsForCivilization(
        civilizationId: CivilizationId,
    ): List<War> = queryMany(
        sql = """
            $WAR_SELECT
            WHERE status IN ('DECLARED', 'ACTIVE')
              AND (declaring_civilization_id = ? OR target_civilization_id = ?)
            ORDER BY declared_at_ms, id
        """.trimIndent(),
        bind = {
            setString(1, civilizationId.toString())
            setString(2, civilizationId.toString())
        },
        map = ResultSet::toWar,
    )

    override fun findBattle(id: BattleId): Battle? = queryOne(
        sql = "$BATTLE_SELECT WHERE id = ?",
        bind = { setString(1, id.toString()) },
        map = ResultSet::toBattle,
    )

    override fun listBattlesForWar(warId: WarId): List<Battle> = queryMany(
        sql = "$BATTLE_SELECT WHERE war_id = ? ORDER BY started_at_ms, id",
        bind = { setString(1, warId.toString()) },
        map = ResultSet::toBattle,
    )

    override fun listBattlesForSeason(seasonId: SeasonId): List<Battle> = queryMany(
        sql = "$BATTLE_SELECT WHERE season_id = ? ORDER BY started_at_ms, id",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toBattle,
    )

    override fun listBattleParticipants(battleId: BattleId): List<BattleParticipant> = queryMany(
        sql = """
            $BATTLE_PARTICIPANT_SELECT
            WHERE battle_id = ?
            ORDER BY side, joined_at_ms, player_id
        """.trimIndent(),
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleParticipant,
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
        const val WAR_SELECT = """
            SELECT id, season_id, declaring_civilization_id, target_civilization_id,
                   declared_by_player_id, status, battle_trigger, destruction_scope,
                   battle_duration_seconds, declared_at_ms, activated_at_ms, ended_at_ms,
                   updated_at_ms
            FROM wars
        """
        const val BATTLE_SELECT = """
            SELECT id, war_id, season_id, attacking_civilization_id,
                   defending_civilization_id, triggered_by_player_id, trigger_claim_id,
                   status, started_at_ms, ends_at_ms, resolving_at_ms, ended_at_ms,
                   outcome, winner_civilization_id, updated_at_ms
            FROM battles
        """
        const val BATTLE_PARTICIPANT_SELECT = """
            SELECT season_id, battle_id, player_id, civilization_id, side, joined_at_ms
            FROM battle_participants
        """
    }
}

private class JdbcWriteContext(
    connection: Connection,
) : JdbcReadContext(connection), CivilizationsWriteContext {
    override fun setActiveSeasonId(seasonId: SeasonId?) {
        val updated = executeUpdate(
            sql = "UPDATE runtime_state SET active_season_id = ? WHERE singleton_id = 1",
        ) {
            setString(1, seasonId?.toString())
        }
        requireUpdated(updated, "Runtime state")
    }

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

    override fun insertWar(war: War) {
        val orderedCivilizationIds = listOf(
            war.declaringCivilizationId.toString(),
            war.targetCivilizationId.toString(),
        ).sorted()
        executeUpdate(
            sql = """
                INSERT INTO wars(
                    id, season_id, declaring_civilization_id, target_civilization_id,
                    declared_by_player_id, status, battle_trigger, destruction_scope,
                    battle_duration_seconds, declared_at_ms, activated_at_ms, ended_at_ms,
                    updated_at_ms, pair_low_id, pair_high_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, war.id.toString())
            setString(2, war.seasonId.toString())
            setString(3, war.declaringCivilizationId.toString())
            setString(4, war.targetCivilizationId.toString())
            setString(5, war.declaredByPlayerId.toString())
            setString(6, war.status.name)
            setString(7, war.rules.battleTrigger.name)
            setString(8, war.rules.destructionScope.name)
            setLong(9, war.rules.battleDurationSeconds)
            setLong(10, war.declaredAt.toEpochMilli())
            setInstantOrNull(11, war.activatedAt)
            setInstantOrNull(12, war.endedAt)
            setLong(13, war.updatedAt.toEpochMilli())
            setString(14, orderedCivilizationIds[0])
            setString(15, orderedCivilizationIds[1])
        }
    }

    override fun updateWar(war: War) {
        val updated = executeUpdate(
            sql = """
                UPDATE wars
                SET status = ?, activated_at_ms = ?, ended_at_ms = ?, updated_at_ms = ?
                WHERE id = ? AND season_id = ?
            """.trimIndent(),
        ) {
            setString(1, war.status.name)
            setInstantOrNull(2, war.activatedAt)
            setInstantOrNull(3, war.endedAt)
            setLong(4, war.updatedAt.toEpochMilli())
            setString(5, war.id.toString())
            setString(6, war.seasonId.toString())
        }
        requireUpdated(updated, "War ${war.id}")
    }

    override fun insertBattle(battle: Battle) {
        executeUpdate(
            sql = """
                INSERT INTO battles(
                    id, war_id, season_id, attacking_civilization_id,
                    defending_civilization_id, triggered_by_player_id, trigger_claim_id,
                    status, started_at_ms, ends_at_ms, resolving_at_ms, ended_at_ms,
                    outcome, winner_civilization_id, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, battle.id.toString())
            setString(2, battle.warId.toString())
            setString(3, battle.seasonId.toString())
            setString(4, battle.attackingCivilizationId.toString())
            setString(5, battle.defendingCivilizationId.toString())
            setString(6, battle.triggeredByPlayerId.toString())
            setString(7, battle.triggerClaimId.toString())
            setString(8, battle.status.name)
            setLong(9, battle.startedAt.toEpochMilli())
            setLong(10, battle.endsAt.toEpochMilli())
            setInstantOrNull(11, battle.resolvingAt)
            setInstantOrNull(12, battle.endedAt)
            setString(13, battle.outcome?.name)
            setString(14, battle.winnerCivilizationId?.toString())
            setLong(15, battle.updatedAt.toEpochMilli())
        }
    }

    override fun updateBattle(battle: Battle) {
        val updated = executeUpdate(
            sql = """
                UPDATE battles
                SET status = ?, resolving_at_ms = ?, ended_at_ms = ?, outcome = ?,
                    winner_civilization_id = ?, updated_at_ms = ?
                WHERE id = ? AND season_id = ?
            """.trimIndent(),
        ) {
            setString(1, battle.status.name)
            setInstantOrNull(2, battle.resolvingAt)
            setInstantOrNull(3, battle.endedAt)
            setString(4, battle.outcome?.name)
            setString(5, battle.winnerCivilizationId?.toString())
            setLong(6, battle.updatedAt.toEpochMilli())
            setString(7, battle.id.toString())
            setString(8, battle.seasonId.toString())
        }
        requireUpdated(updated, "Battle ${battle.id}")
    }

    override fun insertBattleParticipant(participant: BattleParticipant) {
        executeUpdate(
            sql = """
                INSERT INTO battle_participants(
                    season_id, battle_id, player_id, civilization_id, side, joined_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, participant.seasonId.toString())
            setString(2, participant.battleId.toString())
            setString(3, participant.playerId.toString())
            setString(4, participant.civilizationId.toString())
            setString(5, participant.side.name)
            setLong(6, participant.joinedAt.toEpochMilli())
        }
    }

    private fun requireUpdated(updated: Int, description: String) {
        if (updated != 1) {
            throw PersistenceRecordNotFoundException("$description does not exist")
        }
    }
}

private fun PreparedStatement.setInstantOrNull(index: Int, instant: Instant?) {
    if (instant == null) {
        setNull(index, Types.BIGINT)
    } else {
        setLong(index, instant.toEpochMilli())
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

private fun ResultSet.toWar(): War = War(
    id = WarId(uuid("id")),
    seasonId = SeasonId(uuid("season_id")),
    declaringCivilizationId = CivilizationId(uuid("declaring_civilization_id")),
    targetCivilizationId = CivilizationId(uuid("target_civilization_id")),
    declaredByPlayerId = PlayerId(uuid("declared_by_player_id")),
    status = WarStatus.valueOf(getString("status")),
    rules = WarRulesSnapshot(
        battleTrigger = BattleTrigger.valueOf(getString("battle_trigger")),
        destructionScope = LandDestructionScope.valueOf(getString("destruction_scope")),
        battleDurationSeconds = getLong("battle_duration_seconds"),
    ),
    declaredAt = instant("declared_at_ms"),
    activatedAt = nullableInstant("activated_at_ms"),
    endedAt = nullableInstant("ended_at_ms"),
    updatedAt = instant("updated_at_ms"),
)

private fun ResultSet.toBattle(): Battle = Battle(
    id = BattleId(uuid("id")),
    warId = WarId(uuid("war_id")),
    seasonId = SeasonId(uuid("season_id")),
    attackingCivilizationId = CivilizationId(uuid("attacking_civilization_id")),
    defendingCivilizationId = CivilizationId(uuid("defending_civilization_id")),
    triggeredByPlayerId = PlayerId(uuid("triggered_by_player_id")),
    triggerClaimId = ClaimId(uuid("trigger_claim_id")),
    status = BattleStatus.valueOf(getString("status")),
    startedAt = instant("started_at_ms"),
    endsAt = instant("ends_at_ms"),
    resolvingAt = nullableInstant("resolving_at_ms"),
    endedAt = nullableInstant("ended_at_ms"),
    outcome = getString("outcome")?.let(BattleOutcome::valueOf),
    winnerCivilizationId = getString("winner_civilization_id")?.let {
        CivilizationId(UUID.fromString(it))
    },
    updatedAt = instant("updated_at_ms"),
)

private fun ResultSet.toBattleParticipant(): BattleParticipant = BattleParticipant(
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    playerId = PlayerId(uuid("player_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    side = BattleSide.valueOf(getString("side")),
    joinedAt = instant("joined_at_ms"),
)

private fun ResultSet.uuid(column: String): UUID = UUID.fromString(getString(column))

private fun ResultSet.instant(column: String): Instant = Instant.ofEpochMilli(getLong(column))

private fun ResultSet.nullableInstant(column: String): Instant? {
    val millis = getLong(column)
    return if (wasNull()) null else Instant.ofEpochMilli(millis)
}

private fun Connection.rollbackAfter(failure: Throwable) {
    try {
        rollback()
    } catch (rollbackFailure: Throwable) {
        failure.addSuppressed(rollbackFailure)
    }
}
