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
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BattleDamageReport
import io.bennyc.civilizations.domain.damage.BattleDamageReportEntry
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.BlockChangeCursor
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.DamageCostCategory
import io.bennyc.civilizations.domain.damage.DamageReportEligibility
import io.bennyc.civilizations.domain.damage.ReportedBattleBlockChange
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.CivilizationAccount
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.EconomyBridgeDirection
import io.bennyc.civilizations.domain.economy.EconomyBridgeStatus
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransfer
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransferId
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransaction
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.economy.SeasonEconomySettings
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.repair.RepairFundingMode
import io.bennyc.civilizations.domain.repair.RepairJob
import io.bennyc.civilizations.domain.repair.RepairJobId
import io.bennyc.civilizations.domain.repair.RepairJobItem
import io.bennyc.civilizations.domain.repair.RepairJobItemStatus
import io.bennyc.civilizations.domain.repair.RepairJobStatus
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleCasualty
import io.bennyc.civilizations.domain.war.BattleCasualtyEconomics
import io.bennyc.civilizations.domain.war.BattleCasualtyFunding
import io.bennyc.civilizations.domain.war.BattleCombatResolutionCause
import io.bennyc.civilizations.domain.war.BattleCombatRulesSnapshot
import io.bennyc.civilizations.domain.war.BattleCombatState
import io.bennyc.civilizations.domain.war.BattleCombatant
import io.bennyc.civilizations.domain.war.BattleDisconnectPolicy
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleLifeEvent
import io.bennyc.civilizations.domain.war.BattleLifeEventId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleParticipant
import io.bennyc.civilizations.domain.war.BattleSide
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.BattleSurrenderRecord
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

    override fun listOpenBattlesForCivilization(
        civilizationId: CivilizationId,
    ): List<Battle> = queryMany(
        sql = """
            $BATTLE_SELECT
            WHERE status IN ('ACTIVE', 'RESOLVING')
              AND (attacking_civilization_id = ? OR defending_civilization_id = ?)
            ORDER BY started_at_ms, id
        """.trimIndent(),
        bind = {
            setString(1, civilizationId.toString())
            setString(2, civilizationId.toString())
        },
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

    override fun findBattleCombatState(battleId: BattleId): BattleCombatState? = queryOne(
        sql = "$BATTLE_COMBAT_STATE_SELECT WHERE battle_id = ?",
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleCombatState,
    )

    override fun listBattleCombatStatesForSeason(
        seasonId: SeasonId,
    ): List<BattleCombatState> = queryMany(
        sql = "$BATTLE_COMBAT_STATE_SELECT WHERE season_id = ? ORDER BY initialized_at_ms, battle_id",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toBattleCombatState,
    )

    override fun listBattleCombatants(battleId: BattleId): List<BattleCombatant> = queryMany(
        sql = """
            $BATTLE_COMBATANT_SELECT
            WHERE battle_id = ?
            ORDER BY side, enrolled_at_ms, player_id
        """.trimIndent(),
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleCombatant,
    )

    override fun findBattleLifeEvent(id: BattleLifeEventId): BattleLifeEvent? = queryOne(
        sql = "$BATTLE_LIFE_EVENT_SELECT WHERE id = ?",
        bind = { setString(1, id.toString()) },
        map = ResultSet::toBattleLifeEvent,
    )

    override fun listBattleLifeEvents(battleId: BattleId): List<BattleLifeEvent> = queryMany(
        sql = "$BATTLE_LIFE_EVENT_SELECT WHERE battle_id = ? ORDER BY recorded_at_ms, id",
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleLifeEvent,
    )

    override fun findBattleCasualtyEconomics(
        battleId: BattleId,
    ): BattleCasualtyEconomics? = queryOne(
        sql = "$BATTLE_CASUALTY_ECONOMICS_SELECT WHERE battle_id = ?",
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleCasualtyEconomics,
    )

    override fun listBattleCasualtyEconomicsForSeason(
        seasonId: SeasonId,
    ): List<BattleCasualtyEconomics> = queryMany(
        sql = "$BATTLE_CASUALTY_ECONOMICS_SELECT WHERE season_id = ? " +
            "ORDER BY initialized_at_ms, battle_id",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toBattleCasualtyEconomics,
    )

    override fun findBattleCasualty(lifeEventId: BattleLifeEventId): BattleCasualty? = queryOne(
        sql = "$BATTLE_CASUALTY_SELECT WHERE life_event_id = ?",
        bind = { setString(1, lifeEventId.toString()) },
        map = ResultSet::toBattleCasualty,
    )

    override fun listBattleCasualties(battleId: BattleId): List<BattleCasualty> = queryMany(
        sql = "$BATTLE_CASUALTY_SELECT WHERE battle_id = ? ORDER BY recorded_at_ms, life_event_id",
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleCasualty,
    )

    override fun findBattleSurrender(battleId: BattleId): BattleSurrenderRecord? = queryOne(
        sql = "$BATTLE_SURRENDER_SELECT WHERE battle_id = ?",
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleSurrenderRecord,
    )

    override fun listBattleSurrendersForSeason(
        seasonId: SeasonId,
    ): List<BattleSurrenderRecord> = queryMany(
        sql = "$BATTLE_SURRENDER_SELECT WHERE season_id = ? ORDER BY surrendered_at_ms, battle_id",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toBattleSurrenderRecord,
    )

    override fun findSeasonEconomySettings(seasonId: SeasonId): SeasonEconomySettings? = queryOne(
        sql = "$SEASON_ECONOMY_SELECT WHERE season_id = ?",
        bind = { setString(1, seasonId.toString()) },
        map = ResultSet::toSeasonEconomySettings,
    )

    override fun findCivilizationAccount(
        civilizationId: CivilizationId,
    ): CivilizationAccount? = queryOne(
        sql = "$CIVILIZATION_ACCOUNT_SELECT WHERE civilization_id = ?",
        bind = { setString(1, civilizationId.toString()) },
        map = ResultSet::toCivilizationAccount,
    )

    override fun listCivilizationAccounts(seasonId: SeasonId): List<CivilizationAccount> =
        queryMany(
            sql = "$CIVILIZATION_ACCOUNT_SELECT WHERE season_id = ? ORDER BY civilization_id",
            bind = { setString(1, seasonId.toString()) },
            map = ResultSet::toCivilizationAccount,
        )

    override fun findLedgerTransaction(id: LedgerTransactionId): LedgerTransaction? =
        queryOne(
            sql = "$LEDGER_TRANSACTION_SELECT WHERE id = ?",
            bind = { setString(1, id.toString()) },
            map = ResultSet::toLedgerTransactionHeader,
        )?.withPostings()

    override fun findLedgerTransactionByIdempotencyKey(
        idempotencyKey: String,
    ): LedgerTransaction? = queryOne(
        sql = "$LEDGER_TRANSACTION_SELECT WHERE idempotency_key = ?",
        bind = { setString(1, idempotencyKey) },
        map = ResultSet::toLedgerTransactionHeader,
    )?.withPostings()

    override fun listLedgerTransactionsForCivilization(
        civilizationId: CivilizationId,
        limit: Int,
    ): List<LedgerTransaction> {
        require(limit in 1..MAX_LEDGER_PAGE_SIZE) {
            "Ledger page size must be between 1 and $MAX_LEDGER_PAGE_SIZE"
        }
        return queryMany(
            sql = """
                $LEDGER_TRANSACTION_SELECT
                WHERE id IN (
                    SELECT transaction_id
                    FROM economy_ledger_postings
                    WHERE civilization_id = ?
                )
                ORDER BY created_at_ms DESC, id DESC
                LIMIT ?
            """.trimIndent(),
            bind = {
                setString(1, civilizationId.toString())
                setInt(2, limit)
            },
            map = ResultSet::toLedgerTransactionHeader,
        ).map { it.withPostings() }
    }

    override fun findEconomyBridgeTransfer(
        id: EconomyBridgeTransferId,
    ): EconomyBridgeTransfer? = queryOne(
        sql = "$ECONOMY_BRIDGE_SELECT WHERE id = ?",
        bind = { setString(1, id.toString()) },
        map = ResultSet::toEconomyBridgeTransfer,
    )

    override fun findEconomyBridgeTransferByIdempotencyKey(
        idempotencyKey: String,
    ): EconomyBridgeTransfer? = queryOne(
        sql = "$ECONOMY_BRIDGE_SELECT WHERE idempotency_key = ?",
        bind = { setString(1, idempotencyKey) },
        map = ResultSet::toEconomyBridgeTransfer,
    )

    override fun findOpenEconomyBridgeTransferForPlayer(
        playerId: PlayerId,
    ): EconomyBridgeTransfer? = queryOne(
        sql = """
            $ECONOMY_BRIDGE_SELECT
            WHERE player_id = ? AND status IN ('PREPARED', 'RECONCILIATION_REQUIRED')
        """.trimIndent(),
        bind = { setString(1, playerId.toString()) },
        map = ResultSet::toEconomyBridgeTransfer,
    )

    override fun listEconomyBridgeTransfers(
        statuses: Set<EconomyBridgeStatus>,
        limit: Int,
    ): List<EconomyBridgeTransfer> {
        require(statuses.isNotEmpty()) { "At least one economy bridge status is required" }
        require(limit in 1..MAX_ECONOMY_BRIDGE_PAGE_SIZE) {
            "Economy bridge page size must be between 1 and $MAX_ECONOMY_BRIDGE_PAGE_SIZE"
        }
        val orderedStatuses = statuses.sortedBy(EconomyBridgeStatus::name)
        val placeholders = orderedStatuses.joinToString(",") { "?" }
        return queryMany(
            sql = """
                $ECONOMY_BRIDGE_SELECT
                WHERE status IN ($placeholders)
                ORDER BY created_at_ms, id
                LIMIT ?
            """.trimIndent(),
            bind = {
                orderedStatuses.forEachIndexed { index, status ->
                    setString(index + 1, status.name)
                }
                setInt(orderedStatuses.size + 1, limit)
            },
            map = ResultSet::toEconomyBridgeTransfer,
        )
    }

    override fun findRepairJob(id: RepairJobId): RepairJob? = queryOne(
        sql = "$REPAIR_JOB_SELECT WHERE id = ?",
        bind = { setString(1, id.toString()) },
        map = ResultSet::toRepairJob,
    )

    override fun findRepairJobByIdempotencyKey(idempotencyKey: String): RepairJob? = queryOne(
        sql = "$REPAIR_JOB_SELECT WHERE idempotency_key = ?",
        bind = { setString(1, idempotencyKey) },
        map = ResultSet::toRepairJob,
    )

    override fun findOpenRepairJob(
        battleId: BattleId,
        civilizationId: CivilizationId,
    ): RepairJob? = queryOne(
        sql = """
            $REPAIR_JOB_SELECT
            WHERE battle_id = ? AND civilization_id = ?
              AND status IN ('QUEUED', 'RUNNING', 'PAUSED')
        """.trimIndent(),
        bind = {
            setString(1, battleId.toString())
            setString(2, civilizationId.toString())
        },
        map = ResultSet::toRepairJob,
    )

    override fun listRepairJobsForBattle(battleId: BattleId, limit: Int): List<RepairJob> {
        requireRepairPageSize(limit)
        return queryMany(
            sql = """
                $REPAIR_JOB_SELECT
                WHERE battle_id = ?
                ORDER BY created_at_ms DESC, id DESC
                LIMIT ?
            """.trimIndent(),
            bind = {
                setString(1, battleId.toString())
                setInt(2, limit)
            },
            map = ResultSet::toRepairJob,
        )
    }

    override fun listRepairJobsForCivilization(
        civilizationId: CivilizationId,
        limit: Int,
    ): List<RepairJob> {
        requireRepairPageSize(limit)
        return queryMany(
            sql = """
                $REPAIR_JOB_SELECT
                WHERE civilization_id = ?
                ORDER BY created_at_ms DESC, id DESC
                LIMIT ?
            """.trimIndent(),
            bind = {
                setString(1, civilizationId.toString())
                setInt(2, limit)
            },
            map = ResultSet::toRepairJob,
        )
    }

    override fun listRepairJobsByStatus(
        statuses: Set<RepairJobStatus>,
        limit: Int,
    ): List<RepairJob> {
        require(statuses.isNotEmpty()) { "At least one repair status is required" }
        requireRepairPageSize(limit)
        val ordered = statuses.sortedBy(RepairJobStatus::name)
        val placeholders = ordered.joinToString(",") { "?" }
        return queryMany(
            sql = """
                $REPAIR_JOB_SELECT
                WHERE status IN ($placeholders)
                ORDER BY created_at_ms, id
                LIMIT ?
            """.trimIndent(),
            bind = {
                ordered.forEachIndexed { index, status -> setString(index + 1, status.name) }
                setInt(ordered.size + 1, limit)
            },
            map = ResultSet::toRepairJob,
        )
    }

    override fun listRepairJobItems(
        repairJobId: RepairJobId,
        afterOrdinal: Long?,
        limit: Int,
    ): List<RepairJobItem> {
        requireRepairPageSize(limit)
        return queryMany(
            sql = """
                $REPAIR_JOB_ITEM_SELECT
                WHERE repair_job_id = ? AND (? IS NULL OR ordinal > ?)
                ORDER BY ordinal
                LIMIT ?
            """.trimIndent(),
            bind = {
                setString(1, repairJobId.toString())
                if (afterOrdinal == null) {
                    setNull(2, Types.BIGINT)
                    setNull(3, Types.BIGINT)
                } else {
                    setLong(2, afterOrdinal)
                    setLong(3, afterOrdinal)
                }
                setInt(4, limit)
            },
            map = ResultSet::toRepairJobItem,
        )
    }

    override fun findBlockChange(
        battleId: BattleId,
        position: BlockPosition3D,
    ): BattleBlockChange? = queryOne(
        sql = """
            $BLOCK_CHANGE_SELECT
            WHERE battle_id = ? AND world_id = ?
              AND block_x = ? AND block_y = ? AND block_z = ?
        """.trimIndent(),
        bind = {
            setString(1, battleId.toString())
            setString(2, position.worldId.value)
            setInt(3, position.x)
            setInt(4, position.y)
            setInt(5, position.z)
        },
        map = ResultSet::toBattleBlockChange,
    )

    override fun countBlockChanges(battleId: BattleId): Long = requireNotNull(
        queryOne(
            sql = "SELECT COUNT(*) AS change_count FROM battle_block_changes WHERE battle_id = ?",
            bind = { setString(1, battleId.toString()) },
            map = { getLong("change_count") },
        ),
    )

    override fun listBlockChanges(
        battleId: BattleId,
        after: BlockChangeCursor?,
        limit: Int,
    ): List<BattleBlockChange> {
        require(limit in 1..MAX_BLOCK_CHANGE_PAGE_SIZE) {
            "Block change page size must be between 1 and $MAX_BLOCK_CHANGE_PAGE_SIZE"
        }
        return if (after == null) {
            queryMany(
                sql = """
                    $BLOCK_CHANGE_SELECT
                    WHERE battle_id = ?
                    ORDER BY recorded_at_ms, id
                    LIMIT ?
                """.trimIndent(),
                bind = {
                    setString(1, battleId.toString())
                    setInt(2, limit)
                },
                map = ResultSet::toBattleBlockChange,
            )
        } else {
            queryMany(
                sql = """
                    $BLOCK_CHANGE_SELECT
                    WHERE battle_id = ? AND (
                        recorded_at_ms > ? OR (recorded_at_ms = ? AND id > ?)
                    )
                    ORDER BY recorded_at_ms, id
                    LIMIT ?
                """.trimIndent(),
                bind = {
                    setString(1, battleId.toString())
                    setLong(2, after.recordedAt.toEpochMilli())
                    setLong(3, after.recordedAt.toEpochMilli())
                    setString(4, after.id.toString())
                    setInt(5, limit)
                },
                map = ResultSet::toBattleBlockChange,
            )
        }
    }

    override fun findDamageReport(battleId: BattleId): BattleDamageReport? = queryOne(
        sql = "$DAMAGE_REPORT_SELECT WHERE battle_id = ?",
        bind = { setString(1, battleId.toString()) },
        map = ResultSet::toBattleDamageReport,
    )

    override fun findReportedBlockChange(
        battleId: BattleId,
        blockChangeId: BlockChangeId,
    ): ReportedBattleBlockChange? = queryOne(
        sql = """
            $REPORTED_BLOCK_CHANGE_SELECT
            WHERE journal.battle_id = ? AND journal.id = ?
        """.trimIndent(),
        bind = {
            setString(1, battleId.toString())
            setString(2, blockChangeId.toString())
        },
        map = ResultSet::toReportedBattleBlockChange,
    )

    override fun listReportedBlockChanges(
        battleId: BattleId,
        after: BlockChangeCursor?,
        limit: Int,
    ): List<ReportedBattleBlockChange> {
        require(limit in 1..MAX_BLOCK_CHANGE_PAGE_SIZE) {
            "Reported block change page size must be between 1 and $MAX_BLOCK_CHANGE_PAGE_SIZE"
        }
        return if (after == null) {
            queryMany(
                sql = """
                    $REPORTED_BLOCK_CHANGE_SELECT
                    WHERE journal.battle_id = ?
                    ORDER BY journal.recorded_at_ms, journal.id
                    LIMIT ?
                """.trimIndent(),
                bind = {
                    setString(1, battleId.toString())
                    setInt(2, limit)
                },
                map = ResultSet::toReportedBattleBlockChange,
            )
        } else {
            queryMany(
                sql = """
                    $REPORTED_BLOCK_CHANGE_SELECT
                    WHERE journal.battle_id = ? AND (
                        journal.recorded_at_ms > ? OR
                        (journal.recorded_at_ms = ? AND journal.id > ?)
                    )
                    ORDER BY journal.recorded_at_ms, journal.id
                    LIMIT ?
                """.trimIndent(),
                bind = {
                    setString(1, battleId.toString())
                    setLong(2, after.recordedAt.toEpochMilli())
                    setLong(3, after.recordedAt.toEpochMilli())
                    setString(4, after.id.toString())
                    setInt(5, limit)
                },
                map = ResultSet::toReportedBattleBlockChange,
            )
        }
    }

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

    private fun requireRepairPageSize(limit: Int) {
        require(limit in 1..MAX_REPAIR_PAGE_SIZE) {
            "Repair page size must be between 1 and $MAX_REPAIR_PAGE_SIZE"
        }
    }

    private fun LedgerTransactionHeader.withPostings(): LedgerTransaction {
        val postings = queryMany(
            sql = """
                $LEDGER_POSTING_SELECT
                WHERE transaction_id = ?
                ORDER BY civilization_id
            """.trimIndent(),
            bind = { setString(1, id.toString()) },
            map = ResultSet::toLedgerPosting,
        )
        return LedgerTransaction(
            id = id,
            seasonId = seasonId,
            idempotencyKey = idempotencyKey,
            kind = kind,
            referenceType = referenceType,
            referenceId = referenceId,
            actorPlayerId = actorPlayerId,
            description = description,
            currencyScale = currencyScale,
            createdAt = createdAt,
            postings = postings,
        )
    }

    private companion object {
        const val MAX_BLOCK_CHANGE_PAGE_SIZE = 1_000
        const val MAX_ECONOMY_BRIDGE_PAGE_SIZE = 1_000
        const val MAX_LEDGER_PAGE_SIZE = 1_000
        const val MAX_REPAIR_PAGE_SIZE = 1_000

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
        const val BATTLE_COMBAT_STATE_SELECT = """
            SELECT season_id, battle_id, lives_per_combatant, timeout_outcome,
                   disconnect_policy, initialized_at_ms, resolution_cause,
                   requested_outcome, decided_at_ms
            FROM battle_combat_states
        """
        const val BATTLE_COMBATANT_SELECT = """
            SELECT season_id, battle_id, player_id, civilization_id, side,
                   initial_lives, lives_remaining, enrolled_at_ms, eliminated_at_ms
            FROM battle_combatants
        """
        const val BATTLE_LIFE_EVENT_SELECT = """
            SELECT id, season_id, battle_id, player_id, lives_before, lives_after,
                   recorded_at_ms
            FROM battle_life_events
        """
        const val BATTLE_CASUALTY_ECONOMICS_SELECT = """
            SELECT season_id, battle_id, attacker_death_cost_minor,
                   defender_death_cost_minor, attacker_coverage_required,
                   withdrawals_locked, attacker_reserve_minor,
                   reserve_ledger_transaction_id, initialized_at_ms,
                   released_amount_minor, release_ledger_transaction_id, released_at_ms
            FROM battle_casualty_economics
        """
        const val BATTLE_CASUALTY_SELECT = """
            SELECT life_event_id, season_id, battle_id, player_id, civilization_id,
                   side, nominal_cost_minor, charged_amount_minor, unpaid_amount_minor,
                   funding, charge_ledger_transaction_id, recorded_at_ms
            FROM battle_casualties
        """
        const val BATTLE_SURRENDER_SELECT = """
            SELECT season_id, battle_id, surrendered_civilization_id,
                   surrendered_by_player_id, requested_outcome, surrendered_at_ms
            FROM battle_surrenders
        """
        const val SEASON_ECONOMY_SELECT = """
            SELECT season_id, currency_scale, opening_balance_minor, created_at_ms
            FROM season_economy_settings
        """
        const val CIVILIZATION_ACCOUNT_SELECT = """
            SELECT season_id, civilization_id, balance_minor, created_at_ms, updated_at_ms
            FROM civilization_accounts
        """
        const val LEDGER_TRANSACTION_SELECT = """
            SELECT id, season_id, idempotency_key,
                   COALESCE(extended_kind, kind) AS kind,
                   reference_type, reference_id,
                   actor_player_id, description, currency_scale, created_at_ms
            FROM economy_ledger_transactions
        """
        const val LEDGER_POSTING_SELECT = """
            SELECT civilization_id, amount_minor
            FROM economy_ledger_postings
        """
        const val ECONOMY_BRIDGE_SELECT = """
            SELECT id, season_id, civilization_id, player_id, direction, amount_minor,
                   currency_scale, provider_name, idempotency_key, status,
                   ledger_transaction_id, reversal_transaction_id, failure_message,
                   created_at_ms, updated_at_ms, completed_at_ms
            FROM economy_bridge_transfers
        """
        const val REPAIR_JOB_SELECT = """
            SELECT id, season_id, battle_id, civilization_id, initiated_by_player_id,
                   funding_mode, idempotency_key, target_completion_basis_points,
                   total_eligible_count, observed_restored_count,
                   observed_repairable_count, observed_conflict_count,
                   selected_restore_original_count, selected_remove_placement_count,
                   restore_original_unit_price_minor, remove_placement_unit_price_minor,
                   gross_cost_minor, victor_share_basis_points, victor_civilization_id,
                   victor_proceeds_minor, payment_ledger_transaction_id, status,
                   next_item_ordinal, restored_count, skipped_conflict_count, failed_count,
                   created_at_ms, updated_at_ms, completed_at_ms, failure_message
            FROM repair_jobs
        """
        const val REPAIR_JOB_ITEM_SELECT = """
            SELECT repair_job_id, battle_id, block_change_id, ordinal, unit_price_minor,
                   status, processed_at_ms, failure_message
            FROM repair_job_items
        """
        const val BLOCK_CHANGE_SELECT = """
            SELECT id, season_id, battle_id, claim_id, world_id,
                   block_x, block_y, block_z, original_block_data,
                   first_mutation_cause, first_actor_id, recorded_at_ms
            FROM battle_block_changes
        """
        const val DAMAGE_REPORT_SELECT = """
            SELECT season_id, battle_id, journaled_change_count, eligible_change_count,
                   restored_during_battle_count, restore_original_block_count,
                   remove_placed_block_count, generated_at_ms
            FROM battle_damage_reports
        """
        const val REPORTED_BLOCK_CHANGE_SELECT = """
            SELECT journal.id, journal.season_id, journal.battle_id, journal.claim_id,
                   journal.world_id, journal.block_x, journal.block_y, journal.block_z,
                   journal.original_block_data, journal.first_mutation_cause,
                   journal.first_actor_id, journal.recorded_at_ms,
                   entry.final_block_data, entry.eligibility, entry.cost_category
            FROM battle_block_changes journal
            JOIN battle_damage_report_entries entry
              ON entry.battle_id = journal.battle_id
             AND entry.block_change_id = journal.id
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

    override fun insertBattleCombatState(state: BattleCombatState) {
        executeUpdate(
            sql = """
                INSERT INTO battle_combat_states(
                    season_id, battle_id, lives_per_combatant, timeout_outcome,
                    disconnect_policy, initialized_at_ms, resolution_cause,
                    requested_outcome, decided_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, state.seasonId.toString())
            setString(2, state.battleId.toString())
            setInt(3, state.rules.livesPerCombatant)
            setString(4, state.rules.timeoutOutcome.name)
            setString(5, state.rules.disconnectPolicy.name)
            setLong(6, state.initializedAt.toEpochMilli())
            setString(7, state.resolutionCause?.name)
            setString(8, state.requestedOutcome?.name)
            setInstantOrNull(9, state.decidedAt)
        }
    }

    override fun updateBattleCombatState(state: BattleCombatState) {
        val updated = executeUpdate(
            sql = """
                UPDATE battle_combat_states
                SET resolution_cause = ?, requested_outcome = ?, decided_at_ms = ?
                WHERE battle_id = ? AND season_id = ?
            """.trimIndent(),
        ) {
            setString(1, state.resolutionCause?.name)
            setString(2, state.requestedOutcome?.name)
            setInstantOrNull(3, state.decidedAt)
            setString(4, state.battleId.toString())
            setString(5, state.seasonId.toString())
        }
        requireUpdated(updated, "Battle combat state ${state.battleId}")
    }

    override fun insertBattleCombatant(combatant: BattleCombatant) {
        executeUpdate(
            sql = """
                INSERT INTO battle_combatants(
                    season_id, battle_id, player_id, civilization_id, side,
                    initial_lives, lives_remaining, enrolled_at_ms, eliminated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, combatant.seasonId.toString())
            setString(2, combatant.battleId.toString())
            setString(3, combatant.playerId.toString())
            setString(4, combatant.civilizationId.toString())
            setString(5, combatant.side.name)
            setInt(6, combatant.initialLives)
            setInt(7, combatant.livesRemaining)
            setLong(8, combatant.enrolledAt.toEpochMilli())
            setInstantOrNull(9, combatant.eliminatedAt)
        }
    }

    override fun updateBattleCombatant(combatant: BattleCombatant) {
        val updated = executeUpdate(
            sql = """
                UPDATE battle_combatants
                SET lives_remaining = ?, eliminated_at_ms = ?
                WHERE battle_id = ? AND player_id = ? AND season_id = ?
            """.trimIndent(),
        ) {
            setInt(1, combatant.livesRemaining)
            setInstantOrNull(2, combatant.eliminatedAt)
            setString(3, combatant.battleId.toString())
            setString(4, combatant.playerId.toString())
            setString(5, combatant.seasonId.toString())
        }
        requireUpdated(updated, "Battle combatant ${combatant.battleId}/${combatant.playerId}")
    }

    override fun insertBattleLifeEvent(event: BattleLifeEvent) {
        executeUpdate(
            sql = """
                INSERT INTO battle_life_events(
                    id, season_id, battle_id, player_id, lives_before, lives_after,
                    recorded_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, event.id.toString())
            setString(2, event.seasonId.toString())
            setString(3, event.battleId.toString())
            setString(4, event.playerId.toString())
            setInt(5, event.livesBefore)
            setInt(6, event.livesAfter)
            setLong(7, event.recordedAt.toEpochMilli())
        }
    }

    override fun insertBattleCasualtyEconomics(economics: BattleCasualtyEconomics) {
        executeUpdate(
            sql = """
                INSERT INTO battle_casualty_economics(
                    season_id, battle_id, attacker_death_cost_minor,
                    defender_death_cost_minor, attacker_coverage_required,
                    withdrawals_locked, attacker_reserve_minor,
                    reserve_ledger_transaction_id, initialized_at_ms,
                    released_amount_minor, release_ledger_transaction_id, released_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindBattleCasualtyEconomics(economics)
        }
    }

    override fun updateBattleCasualtyEconomics(economics: BattleCasualtyEconomics) {
        val updated = executeUpdate(
            sql = """
                UPDATE battle_casualty_economics
                SET season_id = ?, battle_id = ?, attacker_death_cost_minor = ?,
                    defender_death_cost_minor = ?, attacker_coverage_required = ?,
                    withdrawals_locked = ?, attacker_reserve_minor = ?,
                    reserve_ledger_transaction_id = ?, initialized_at_ms = ?,
                    released_amount_minor = ?, release_ledger_transaction_id = ?,
                    released_at_ms = ?
                WHERE battle_id = ?
            """.trimIndent(),
        ) {
            bindBattleCasualtyEconomics(economics)
            setString(13, economics.battleId.toString())
        }
        requireUpdated(updated, "Battle casualty economics ${economics.battleId}")
    }

    override fun insertBattleCasualty(casualty: BattleCasualty) {
        executeUpdate(
            sql = """
                INSERT INTO battle_casualties(
                    life_event_id, season_id, battle_id, player_id, civilization_id,
                    side, nominal_cost_minor, charged_amount_minor, unpaid_amount_minor,
                    funding, charge_ledger_transaction_id, recorded_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, casualty.lifeEventId.toString())
            setString(2, casualty.seasonId.toString())
            setString(3, casualty.battleId.toString())
            setString(4, casualty.playerId.toString())
            setString(5, casualty.civilizationId.toString())
            setString(6, casualty.side.name)
            setLong(7, casualty.nominalCost.minorUnits)
            setLong(8, casualty.chargedAmount.minorUnits)
            setLong(9, casualty.unpaidAmount.minorUnits)
            setString(10, casualty.funding.name)
            setString(11, casualty.chargeLedgerTransactionId?.toString())
            setLong(12, casualty.recordedAt.toEpochMilli())
        }
    }

    override fun insertBattleSurrender(surrender: BattleSurrenderRecord) {
        executeUpdate(
            sql = """
                INSERT INTO battle_surrenders(
                    season_id, battle_id, surrendered_civilization_id,
                    surrendered_by_player_id, requested_outcome, surrendered_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, surrender.seasonId.toString())
            setString(2, surrender.battleId.toString())
            setString(3, surrender.surrenderedCivilizationId.toString())
            setString(4, surrender.surrenderedByPlayerId.toString())
            setString(5, surrender.requestedOutcome.name)
            setLong(6, surrender.surrenderedAt.toEpochMilli())
        }
    }

    override fun insertSeasonEconomySettings(settings: SeasonEconomySettings) {
        executeUpdate(
            sql = """
                INSERT INTO season_economy_settings(
                    season_id, currency_scale, opening_balance_minor, created_at_ms
                ) VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, settings.seasonId.toString())
            setInt(2, settings.currencyScale.decimalPlaces)
            setLong(3, settings.openingBalance.minorUnits)
            setLong(4, settings.createdAt.toEpochMilli())
        }
    }

    override fun insertCivilizationAccount(account: CivilizationAccount) {
        executeUpdate(
            sql = """
                INSERT INTO civilization_accounts(
                    season_id, civilization_id, balance_minor, created_at_ms, updated_at_ms
                ) VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, account.seasonId.toString())
            setString(2, account.civilizationId.toString())
            setLong(3, account.balance.minorUnits)
            setLong(4, account.createdAt.toEpochMilli())
            setLong(5, account.updatedAt.toEpochMilli())
        }
    }

    override fun insertLedgerTransaction(transaction: LedgerTransaction) {
        val extendedKind = transaction.kind.takeIf { it.isExtendedLedgerKind }
        val storedKind = if (extendedKind == null) {
            transaction.kind
        } else {
            LedgerTransactionKind.ADMIN_ADJUSTMENT
        }
        executeUpdate(
            sql = """
                INSERT INTO economy_ledger_transactions(
                    id, season_id, idempotency_key, kind, reference_type, reference_id,
                    actor_player_id, description, currency_scale, posting_count, created_at_ms,
                    extended_kind
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, transaction.id.toString())
            setString(2, transaction.seasonId.toString())
            setString(3, transaction.idempotencyKey)
            setString(4, storedKind.name)
            setString(5, transaction.referenceType)
            setString(6, transaction.referenceId)
            setString(7, transaction.actorPlayerId?.toString())
            setString(8, transaction.description)
            setInt(9, transaction.currencyScale.decimalPlaces)
            setInt(10, transaction.postings.size)
            setLong(11, transaction.createdAt.toEpochMilli())
            setString(12, extendedKind?.name)
        }
        transaction.postings.forEach { posting ->
            executeUpdate(
                sql = """
                    INSERT INTO economy_ledger_postings(
                        transaction_id, season_id, civilization_id, amount_minor
                    ) VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ) {
                setString(1, transaction.id.toString())
                setString(2, transaction.seasonId.toString())
                setString(3, posting.civilizationId.toString())
                setLong(4, posting.amount.minorUnits)
            }
        }
    }

    override fun insertEconomyBridgeTransfer(transfer: EconomyBridgeTransfer) {
        executeUpdate(
            sql = """
                INSERT INTO economy_bridge_transfers(
                    id, season_id, civilization_id, player_id, direction, amount_minor,
                    currency_scale, provider_name, idempotency_key, status,
                    ledger_transaction_id, reversal_transaction_id, failure_message,
                    created_at_ms, updated_at_ms, completed_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindEconomyBridgeTransfer(transfer)
        }
    }

    override fun updateEconomyBridgeTransfer(transfer: EconomyBridgeTransfer) {
        val updated = executeUpdate(
            sql = """
                UPDATE economy_bridge_transfers
                SET season_id = ?, civilization_id = ?, player_id = ?, direction = ?,
                    amount_minor = ?, currency_scale = ?, provider_name = ?,
                    idempotency_key = ?, status = ?, ledger_transaction_id = ?,
                    reversal_transaction_id = ?, failure_message = ?, created_at_ms = ?,
                    updated_at_ms = ?, completed_at_ms = ?
                WHERE id = ?
            """.trimIndent(),
        ) {
            setString(1, transfer.seasonId.toString())
            setString(2, transfer.civilizationId.toString())
            setString(3, transfer.playerId.toString())
            setString(4, transfer.direction.name)
            setLong(5, transfer.amount.minorUnits)
            setInt(6, transfer.currencyScale.decimalPlaces)
            setString(7, transfer.providerName)
            setString(8, transfer.idempotencyKey)
            setString(9, transfer.status.name)
            setString(10, transfer.ledgerTransactionId?.toString())
            setString(11, transfer.reversalTransactionId?.toString())
            setString(12, transfer.failureMessage)
            setLong(13, transfer.createdAt.toEpochMilli())
            setLong(14, transfer.updatedAt.toEpochMilli())
            setInstantOrNull(15, transfer.completedAt)
            setString(16, transfer.id.toString())
        }
        requireUpdated(updated, "Economy bridge transfer ${transfer.id}")
    }

    override fun insertRepairJob(job: RepairJob) {
        executeUpdate(
            sql = """
                INSERT INTO repair_jobs(
                    id, season_id, battle_id, civilization_id, initiated_by_player_id,
                    funding_mode, idempotency_key, target_completion_basis_points,
                    total_eligible_count, observed_restored_count,
                    observed_repairable_count, observed_conflict_count,
                    selected_restore_original_count, selected_remove_placement_count,
                    restore_original_unit_price_minor, remove_placement_unit_price_minor,
                    gross_cost_minor, victor_share_basis_points, victor_civilization_id,
                    victor_proceeds_minor, payment_ledger_transaction_id, status,
                    next_item_ordinal, restored_count, skipped_conflict_count, failed_count,
                    created_at_ms, updated_at_ms, completed_at_ms, failure_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                          ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindRepairJob(job, includeId = true)
        }
    }

    override fun updateRepairJob(job: RepairJob) {
        val updated = executeUpdate(
            sql = """
                UPDATE repair_jobs
                SET season_id = ?, battle_id = ?, civilization_id = ?,
                    initiated_by_player_id = ?, funding_mode = ?, idempotency_key = ?,
                    target_completion_basis_points = ?, total_eligible_count = ?,
                    observed_restored_count = ?, observed_repairable_count = ?,
                    observed_conflict_count = ?, selected_restore_original_count = ?,
                    selected_remove_placement_count = ?,
                    restore_original_unit_price_minor = ?,
                    remove_placement_unit_price_minor = ?, gross_cost_minor = ?,
                    victor_share_basis_points = ?, victor_civilization_id = ?,
                    victor_proceeds_minor = ?, payment_ledger_transaction_id = ?, status = ?,
                    next_item_ordinal = ?, restored_count = ?, skipped_conflict_count = ?,
                    failed_count = ?, created_at_ms = ?, updated_at_ms = ?,
                    completed_at_ms = ?, failure_message = ?
                WHERE id = ?
            """.trimIndent(),
        ) {
            bindRepairJob(job, includeId = false)
            setString(30, job.id.toString())
        }
        requireUpdated(updated, "Repair job ${job.id}")
    }

    override fun insertRepairJobItem(item: RepairJobItem) {
        executeUpdate(
            sql = """
                INSERT INTO repair_job_items(
                    repair_job_id, battle_id, block_change_id, ordinal, unit_price_minor,
                    status, processed_at_ms, failure_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            bindRepairJobItem(item)
        }
    }

    override fun updateRepairJobItem(item: RepairJobItem) {
        val updated = executeUpdate(
            sql = """
                UPDATE repair_job_items
                SET battle_id = ?, block_change_id = ?, ordinal = ?, unit_price_minor = ?,
                    status = ?, processed_at_ms = ?, failure_message = ?
                WHERE repair_job_id = ? AND block_change_id = ?
            """.trimIndent(),
        ) {
            setString(1, item.battleId.toString())
            setString(2, item.blockChangeId.toString())
            setLong(3, item.ordinal)
            setLong(4, item.unitPrice.minorUnits)
            setString(5, item.status.name)
            setInstantOrNull(6, item.processedAt)
            setString(7, item.failureMessage)
            setString(8, item.repairJobId.toString())
            setString(9, item.blockChangeId.toString())
        }
        requireUpdated(updated, "Repair item ${item.repairJobId}/${item.blockChangeId}")
    }

    private fun PreparedStatement.bindRepairJob(job: RepairJob, includeId: Boolean) {
        var index = 1
        if (includeId) setString(index++, job.id.toString())
        setString(index++, job.seasonId.toString())
        setString(index++, job.battleId.toString())
        setString(index++, job.civilizationId.toString())
        setString(index++, job.initiatedByPlayerId?.toString())
        setString(index++, job.fundingMode.name)
        setString(index++, job.idempotencyKey)
        setInt(index++, job.targetCompletionBasisPoints)
        setLong(index++, job.totalEligibleCount)
        setLong(index++, job.observedRestoredCount)
        setLong(index++, job.observedRepairableCount)
        setLong(index++, job.observedConflictCount)
        setLong(index++, job.selectedRestoreOriginalCount)
        setLong(index++, job.selectedRemovePlacementCount)
        setLong(index++, job.restoreOriginalUnitPrice.minorUnits)
        setLong(index++, job.removePlacementUnitPrice.minorUnits)
        setLong(index++, job.grossCost.minorUnits)
        setInt(index++, job.victorShareBasisPoints)
        setString(index++, job.victorCivilizationId?.toString())
        setLong(index++, job.victorProceeds.minorUnits)
        setString(index++, job.paymentLedgerTransactionId?.toString())
        setString(index++, job.status.name)
        setLong(index++, job.nextItemOrdinal)
        setLong(index++, job.restoredCount)
        setLong(index++, job.skippedConflictCount)
        setLong(index++, job.failedCount)
        setLong(index++, job.createdAt.toEpochMilli())
        setLong(index++, job.updatedAt.toEpochMilli())
        setInstantOrNull(index++, job.completedAt)
        setString(index, job.failureMessage)
    }

    private fun PreparedStatement.bindBattleCasualtyEconomics(
        economics: BattleCasualtyEconomics,
    ) {
        setString(1, economics.seasonId.toString())
        setString(2, economics.battleId.toString())
        setLong(3, economics.attackerDeathCost.minorUnits)
        setLong(4, economics.defenderDeathCost.minorUnits)
        setInt(5, if (economics.attackerCoverageRequired) 1 else 0)
        setInt(6, if (economics.withdrawalsLocked) 1 else 0)
        setLong(7, economics.attackerReserve.minorUnits)
        setString(8, economics.reserveLedgerTransactionId?.toString())
        setLong(9, economics.initializedAt.toEpochMilli())
        if (economics.releasedAmount == null) {
            setNull(10, Types.BIGINT)
        } else {
            setLong(10, economics.releasedAmount.minorUnits)
        }
        setString(11, economics.releaseLedgerTransactionId?.toString())
        setInstantOrNull(12, economics.releasedAt)
    }

    private fun PreparedStatement.bindRepairJobItem(item: RepairJobItem) {
        setString(1, item.repairJobId.toString())
        setString(2, item.battleId.toString())
        setString(3, item.blockChangeId.toString())
        setLong(4, item.ordinal)
        setLong(5, item.unitPrice.minorUnits)
        setString(6, item.status.name)
        setInstantOrNull(7, item.processedAt)
        setString(8, item.failureMessage)
    }

    private fun PreparedStatement.bindEconomyBridgeTransfer(transfer: EconomyBridgeTransfer) {
        setString(1, transfer.id.toString())
        setString(2, transfer.seasonId.toString())
        setString(3, transfer.civilizationId.toString())
        setString(4, transfer.playerId.toString())
        setString(5, transfer.direction.name)
        setLong(6, transfer.amount.minorUnits)
        setInt(7, transfer.currencyScale.decimalPlaces)
        setString(8, transfer.providerName)
        setString(9, transfer.idempotencyKey)
        setString(10, transfer.status.name)
        setString(11, transfer.ledgerTransactionId?.toString())
        setString(12, transfer.reversalTransactionId?.toString())
        setString(13, transfer.failureMessage)
        setLong(14, transfer.createdAt.toEpochMilli())
        setLong(15, transfer.updatedAt.toEpochMilli())
        setInstantOrNull(16, transfer.completedAt)
    }

    override fun insertBlockChangeIfAbsent(blockChange: BattleBlockChange): Boolean =
        executeUpdate(
            sql = """
                INSERT INTO battle_block_changes(
                    id, season_id, battle_id, claim_id, world_id,
                    block_x, block_y, block_z, original_block_data,
                    first_mutation_cause, first_actor_id, recorded_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(battle_id, world_id, block_x, block_y, block_z) DO NOTHING
            """.trimIndent(),
        ) {
            setString(1, blockChange.id.toString())
            setString(2, blockChange.seasonId.toString())
            setString(3, blockChange.battleId.toString())
            setString(4, blockChange.claimId.toString())
            setString(5, blockChange.position.worldId.value)
            setInt(6, blockChange.position.x)
            setInt(7, blockChange.position.y)
            setInt(8, blockChange.position.z)
            setString(9, blockChange.originalState.blockData)
            setString(10, blockChange.firstMutationCause.name)
            setString(11, blockChange.firstActorId.toString())
            setLong(12, blockChange.recordedAt.toEpochMilli())
        } == 1

    override fun insertDamageReportEntry(entry: BattleDamageReportEntry) {
        executeUpdate(
            sql = """
                INSERT INTO battle_damage_report_entries(
                    season_id, battle_id, block_change_id, final_block_data,
                    eligibility, cost_category
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, entry.seasonId.toString())
            setString(2, entry.battleId.toString())
            setString(3, entry.blockChangeId.toString())
            setString(4, entry.finalState.blockData)
            setString(5, entry.eligibility.name)
            setString(6, entry.costCategory?.name)
        }
    }

    override fun insertDamageReport(report: BattleDamageReport) {
        executeUpdate(
            sql = """
                INSERT INTO battle_damage_reports(
                    season_id, battle_id, journaled_change_count, eligible_change_count,
                    restored_during_battle_count, restore_original_block_count,
                    remove_placed_block_count, generated_at_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ) {
            setString(1, report.seasonId.toString())
            setString(2, report.battleId.toString())
            setLong(3, report.journaledChangeCount)
            setLong(4, report.eligibleChangeCount)
            setLong(5, report.restoredDuringBattleCount)
            setLong(6, report.restoreOriginalBlockCount)
            setLong(7, report.removePlacedBlockCount)
            setLong(8, report.generatedAt.toEpochMilli())
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

private val LedgerTransactionKind.isExtendedLedgerKind: Boolean
    get() = this == LedgerTransactionKind.BATTLE_CASUALTY_RESERVE ||
        this == LedgerTransactionKind.BATTLE_CASUALTY_CHARGE ||
        this == LedgerTransactionKind.BATTLE_CASUALTY_RELEASE

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

private fun ResultSet.toBattleCombatState(): BattleCombatState = BattleCombatState(
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    rules = BattleCombatRulesSnapshot(
        livesPerCombatant = getInt("lives_per_combatant"),
        timeoutOutcome = BattleOutcome.valueOf(getString("timeout_outcome")),
        disconnectPolicy = BattleDisconnectPolicy.valueOf(getString("disconnect_policy")),
    ),
    initializedAt = instant("initialized_at_ms"),
    resolutionCause = getString("resolution_cause")?.let(BattleCombatResolutionCause::valueOf),
    requestedOutcome = getString("requested_outcome")?.let(BattleOutcome::valueOf),
    decidedAt = nullableInstant("decided_at_ms"),
)

private fun ResultSet.toBattleCombatant(): BattleCombatant = BattleCombatant(
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    playerId = PlayerId(uuid("player_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    side = BattleSide.valueOf(getString("side")),
    initialLives = getInt("initial_lives"),
    livesRemaining = getInt("lives_remaining"),
    enrolledAt = instant("enrolled_at_ms"),
    eliminatedAt = nullableInstant("eliminated_at_ms"),
)

private fun ResultSet.toBattleLifeEvent(): BattleLifeEvent = BattleLifeEvent(
    id = BattleLifeEventId(uuid("id")),
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    playerId = PlayerId(uuid("player_id")),
    livesBefore = getInt("lives_before"),
    livesAfter = getInt("lives_after"),
    recordedAt = instant("recorded_at_ms"),
)

private fun ResultSet.toBattleCasualtyEconomics(): BattleCasualtyEconomics =
    BattleCasualtyEconomics(
        seasonId = SeasonId(uuid("season_id")),
        battleId = BattleId(uuid("battle_id")),
        attackerDeathCost = MoneyAmount(getLong("attacker_death_cost_minor")),
        defenderDeathCost = MoneyAmount(getLong("defender_death_cost_minor")),
        attackerCoverageRequired = getInt("attacker_coverage_required") != 0,
        withdrawalsLocked = getInt("withdrawals_locked") != 0,
        attackerReserve = MoneyAmount(getLong("attacker_reserve_minor")),
        reserveLedgerTransactionId = getString("reserve_ledger_transaction_id")?.let {
            LedgerTransactionId(UUID.fromString(it))
        },
        initializedAt = instant("initialized_at_ms"),
        releasedAmount = nullableLong("released_amount_minor")?.let(::MoneyAmount),
        releaseLedgerTransactionId = getString("release_ledger_transaction_id")?.let {
            LedgerTransactionId(UUID.fromString(it))
        },
        releasedAt = nullableInstant("released_at_ms"),
    )

private fun ResultSet.toBattleCasualty(): BattleCasualty = BattleCasualty(
    lifeEventId = BattleLifeEventId(uuid("life_event_id")),
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    playerId = PlayerId(uuid("player_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    side = BattleSide.valueOf(getString("side")),
    nominalCost = MoneyAmount(getLong("nominal_cost_minor")),
    chargedAmount = MoneyAmount(getLong("charged_amount_minor")),
    unpaidAmount = MoneyAmount(getLong("unpaid_amount_minor")),
    funding = BattleCasualtyFunding.valueOf(getString("funding")),
    chargeLedgerTransactionId = getString("charge_ledger_transaction_id")?.let {
        LedgerTransactionId(UUID.fromString(it))
    },
    recordedAt = instant("recorded_at_ms"),
)

private fun ResultSet.toBattleSurrenderRecord(): BattleSurrenderRecord = BattleSurrenderRecord(
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    surrenderedCivilizationId = CivilizationId(uuid("surrendered_civilization_id")),
    surrenderedByPlayerId = PlayerId(uuid("surrendered_by_player_id")),
    requestedOutcome = BattleOutcome.valueOf(getString("requested_outcome")),
    surrenderedAt = instant("surrendered_at_ms"),
)

private fun ResultSet.toSeasonEconomySettings(): SeasonEconomySettings = SeasonEconomySettings(
    seasonId = SeasonId(uuid("season_id")),
    currencyScale = CurrencyScale(getInt("currency_scale")),
    openingBalance = MoneyAmount(getLong("opening_balance_minor")),
    createdAt = instant("created_at_ms"),
)

private fun ResultSet.toCivilizationAccount(): CivilizationAccount = CivilizationAccount(
    seasonId = SeasonId(uuid("season_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    balance = MoneyAmount(getLong("balance_minor")),
    createdAt = instant("created_at_ms"),
    updatedAt = instant("updated_at_ms"),
)

private data class LedgerTransactionHeader(
    val id: LedgerTransactionId,
    val seasonId: SeasonId,
    val idempotencyKey: String,
    val kind: LedgerTransactionKind,
    val referenceType: String?,
    val referenceId: String?,
    val actorPlayerId: PlayerId?,
    val description: String,
    val currencyScale: CurrencyScale,
    val createdAt: Instant,
)

private fun ResultSet.toLedgerTransactionHeader(): LedgerTransactionHeader =
    LedgerTransactionHeader(
        id = LedgerTransactionId(uuid("id")),
        seasonId = SeasonId(uuid("season_id")),
        idempotencyKey = getString("idempotency_key"),
        kind = LedgerTransactionKind.valueOf(getString("kind")),
        referenceType = getString("reference_type"),
        referenceId = getString("reference_id"),
        actorPlayerId = getString("actor_player_id")?.let { PlayerId(UUID.fromString(it)) },
        description = getString("description"),
        currencyScale = CurrencyScale(getInt("currency_scale")),
        createdAt = instant("created_at_ms"),
    )

private fun ResultSet.toLedgerPosting(): LedgerPosting = LedgerPosting(
    civilizationId = CivilizationId(uuid("civilization_id")),
    amount = MoneyAmount(getLong("amount_minor")),
)

private fun ResultSet.toEconomyBridgeTransfer(): EconomyBridgeTransfer = EconomyBridgeTransfer(
    id = EconomyBridgeTransferId(uuid("id")),
    seasonId = SeasonId(uuid("season_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    playerId = PlayerId(uuid("player_id")),
    direction = EconomyBridgeDirection.valueOf(getString("direction")),
    amount = MoneyAmount(getLong("amount_minor")),
    currencyScale = CurrencyScale(getInt("currency_scale")),
    providerName = getString("provider_name"),
    idempotencyKey = getString("idempotency_key"),
    status = EconomyBridgeStatus.valueOf(getString("status")),
    ledgerTransactionId = getString("ledger_transaction_id")?.let {
        LedgerTransactionId(UUID.fromString(it))
    },
    reversalTransactionId = getString("reversal_transaction_id")?.let {
        LedgerTransactionId(UUID.fromString(it))
    },
    failureMessage = getString("failure_message"),
    createdAt = instant("created_at_ms"),
    updatedAt = instant("updated_at_ms"),
    completedAt = nullableInstant("completed_at_ms"),
)

private fun ResultSet.toRepairJob(): RepairJob = RepairJob(
    id = RepairJobId(uuid("id")),
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    civilizationId = CivilizationId(uuid("civilization_id")),
    initiatedByPlayerId = getString("initiated_by_player_id")?.let {
        PlayerId(UUID.fromString(it))
    },
    fundingMode = RepairFundingMode.valueOf(getString("funding_mode")),
    idempotencyKey = getString("idempotency_key"),
    targetCompletionBasisPoints = getInt("target_completion_basis_points"),
    totalEligibleCount = getLong("total_eligible_count"),
    observedRestoredCount = getLong("observed_restored_count"),
    observedRepairableCount = getLong("observed_repairable_count"),
    observedConflictCount = getLong("observed_conflict_count"),
    selectedRestoreOriginalCount = getLong("selected_restore_original_count"),
    selectedRemovePlacementCount = getLong("selected_remove_placement_count"),
    restoreOriginalUnitPrice = MoneyAmount(getLong("restore_original_unit_price_minor")),
    removePlacementUnitPrice = MoneyAmount(getLong("remove_placement_unit_price_minor")),
    grossCost = MoneyAmount(getLong("gross_cost_minor")),
    victorShareBasisPoints = getInt("victor_share_basis_points"),
    victorCivilizationId = getString("victor_civilization_id")?.let {
        CivilizationId(UUID.fromString(it))
    },
    victorProceeds = MoneyAmount(getLong("victor_proceeds_minor")),
    paymentLedgerTransactionId = getString("payment_ledger_transaction_id")?.let {
        LedgerTransactionId(UUID.fromString(it))
    },
    status = RepairJobStatus.valueOf(getString("status")),
    nextItemOrdinal = getLong("next_item_ordinal"),
    restoredCount = getLong("restored_count"),
    skippedConflictCount = getLong("skipped_conflict_count"),
    failedCount = getLong("failed_count"),
    createdAt = instant("created_at_ms"),
    updatedAt = instant("updated_at_ms"),
    completedAt = nullableInstant("completed_at_ms"),
    failureMessage = getString("failure_message"),
)

private fun ResultSet.toRepairJobItem(): RepairJobItem = RepairJobItem(
    repairJobId = RepairJobId(uuid("repair_job_id")),
    battleId = BattleId(uuid("battle_id")),
    blockChangeId = BlockChangeId(uuid("block_change_id")),
    ordinal = getLong("ordinal"),
    unitPrice = MoneyAmount(getLong("unit_price_minor")),
    status = RepairJobItemStatus.valueOf(getString("status")),
    processedAt = nullableInstant("processed_at_ms"),
    failureMessage = getString("failure_message"),
)

private fun ResultSet.toBattleBlockChange(): BattleBlockChange = BattleBlockChange(
    id = BlockChangeId(uuid("id")),
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    claimId = ClaimId(uuid("claim_id")),
    position = BlockPosition3D(
        worldId = WorldId(getString("world_id")),
        x = getInt("block_x"),
        y = getInt("block_y"),
        z = getInt("block_z"),
    ),
    originalState = SimpleBlockSnapshot(getString("original_block_data")),
    firstMutationCause = BlockMutationCause.valueOf(getString("first_mutation_cause")),
    firstActorId = PlayerId(uuid("first_actor_id")),
    recordedAt = instant("recorded_at_ms"),
)

private fun ResultSet.toBattleDamageReport(): BattleDamageReport = BattleDamageReport(
    seasonId = SeasonId(uuid("season_id")),
    battleId = BattleId(uuid("battle_id")),
    journaledChangeCount = getLong("journaled_change_count"),
    eligibleChangeCount = getLong("eligible_change_count"),
    restoredDuringBattleCount = getLong("restored_during_battle_count"),
    restoreOriginalBlockCount = getLong("restore_original_block_count"),
    removePlacedBlockCount = getLong("remove_placed_block_count"),
    generatedAt = instant("generated_at_ms"),
)

private fun ResultSet.toReportedBattleBlockChange(): ReportedBattleBlockChange {
    val journalEntry = toBattleBlockChange()
    return ReportedBattleBlockChange(
        journalEntry = journalEntry,
        reportEntry = BattleDamageReportEntry(
            seasonId = journalEntry.seasonId,
            battleId = journalEntry.battleId,
            blockChangeId = journalEntry.id,
            finalState = SimpleBlockSnapshot(getString("final_block_data")),
            eligibility = DamageReportEligibility.valueOf(getString("eligibility")),
            costCategory = getString("cost_category")?.let(DamageCostCategory::valueOf),
        ),
    )
}

private fun ResultSet.uuid(column: String): UUID = UUID.fromString(getString(column))

private fun ResultSet.instant(column: String): Instant = Instant.ofEpochMilli(getLong(column))

private fun ResultSet.nullableInstant(column: String): Instant? {
    val millis = getLong(column)
    return if (wasNull()) null else Instant.ofEpochMilli(millis)
}

private fun ResultSet.nullableLong(column: String): Long? {
    val value = getLong(column)
    return if (wasNull()) null else value
}

private fun Connection.rollbackAfter(failure: Throwable) {
    try {
        rollback()
    } catch (rollbackFailure: Throwable) {
        failure.addSuppressed(rollbackFailure)
    }
}
