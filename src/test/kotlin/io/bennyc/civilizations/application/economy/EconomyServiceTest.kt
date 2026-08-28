package io.bennyc.civilizations.application.economy

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.application.support.unchangedValue
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.EconomyBridgeStatus
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class EconomyServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `opening balances and civilization transfers are exact durable and idempotent`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)

            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()
            assertEquals(MoneyAmount(500), fixture.balance(fixture.northId))
            assertEquals(MoneyAmount(500), fixture.balance(fixture.southId))

            val request = LedgerTransactionRequest(
                seasonId = fixture.seasonId,
                idempotencyKey = "transfer:north-south:1",
                kind = LedgerTransactionKind.CIVILIZATION_TRANSFER,
                postings = listOf(
                    LedgerPosting(fixture.northId, MoneyAmount(-125)),
                    LedgerPosting(fixture.southId, MoneyAmount(125)),
                ),
                referenceType = "TEST",
                referenceId = "one",
                actorPlayerId = playerId(1),
                description = "Test civilization transfer",
                allowDebt = false,
            )

            fixture.economy.post(request).appliedValue()
            fixture.economy.post(request).unchangedValue()
            assertEquals(MoneyAmount(375), fixture.balance(fixture.northId))
            assertEquals(MoneyAmount(625), fixture.balance(fixture.southId))
            assertEquals(
                listOf(LedgerTransactionKind.CIVILIZATION_TRANSFER, LedgerTransactionKind.OPENING_BALANCE),
                fixture.economy.listLedger(fixture.northId).map { it.kind },
            )
        }
    }

    @Test
    fun `completed ledger transactions reject later appended postings`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()
            val opening = checkNotNull(database.repository.read {
                findLedgerTransactionByIdempotencyKey(
                    "opening:${fixture.seasonId}:${fixture.northId}",
                )
            })

            database.connectionFactory.open().use { connection ->
                assertFailsWith<java.sql.SQLException> {
                    connection.prepareStatement(
                        """
                        INSERT INTO economy_ledger_postings(
                            transaction_id, season_id, civilization_id, amount_minor
                        ) VALUES (?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, opening.id.toString())
                        statement.setString(2, fixture.seasonId.toString())
                        statement.setString(3, fixture.southId.toString())
                        statement.setLong(4, 1)
                        statement.executeUpdate()
                    }
                }
            }
            assertEquals(MoneyAmount(500), fixture.balance(fixture.southId))
        }
    }

    @Test
    fun `player deposit credits treasury only after external success`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()
            val request = fixture.playerTransfer("deposit:one", MoneyAmount(250))

            val prepared = fixture.economy.preparePlayerDeposit(request).appliedValue()
            assertEquals(EconomyBridgeStatus.PREPARED, prepared.status)
            assertEquals(MoneyAmount(500), fixture.balance(fixture.northId))

            val completed = fixture.economy.completeExternalTransfer(prepared.id).appliedValue()
            assertEquals(EconomyBridgeStatus.COMPLETED, completed.status)
            assertEquals(MoneyAmount(750), fixture.balance(fixture.northId))
            assertEquals(completed, fixture.economy.completeExternalTransfer(prepared.id).unchangedValue())
        }
    }

    @Test
    fun `failed withdrawal reverses its reserved treasury debit`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()

            val prepared = fixture.economy.preparePlayerWithdrawal(
                fixture.playerTransfer("withdraw:one", MoneyAmount(200)),
            ).appliedValue()
            assertEquals(MoneyAmount(300), fixture.balance(fixture.northId))

            val failed = fixture.economy.failExternalTransfer(
                prepared.id,
                "player wallet rejected deposit",
            ).appliedValue()
            assertEquals(EconomyBridgeStatus.EXTERNAL_FAILED, failed.status)
            assertEquals(MoneyAmount(500), fixture.balance(fixture.northId))
        }
    }

    @Test
    fun `startup recovery never retries ambiguous external work and requires admin decision`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()
            val prepared = fixture.economy.preparePlayerWithdrawal(
                fixture.playerTransfer("withdraw:crash", MoneyAmount(100)),
            ).appliedValue()
            assertEquals(MoneyAmount(400), fixture.balance(fixture.northId))

            assertEquals(1, fixture.economy.recoverInterruptedBridgeTransfers())
            assertEquals(0, fixture.economy.recoverInterruptedBridgeTransfers())
            val pending = fixture.economy.listReconciliationRequired().single()
            assertEquals(prepared.id, pending.id)

            val cancelled = fixture.economy.reconcileBridgeTransfer(
                ReconcileEconomyBridgeTransfer(
                    transferId = prepared.id,
                    externalOperationSucceeded = false,
                    adminPlayerId = null,
                    reason = "Provider history confirms no player credit",
                ),
            ).appliedValue()
            assertEquals(EconomyBridgeStatus.RECONCILED_CANCELLED, cancelled.status)
            assertEquals(MoneyAmount(500), fixture.balance(fixture.northId))
        }
    }

    @Test
    fun `provider exceptions can be marked ambiguous without reversing or crediting money`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()
            val prepared = fixture.economy.preparePlayerWithdrawal(
                fixture.playerTransfer("withdraw:exception", MoneyAmount(100)),
            ).appliedValue()

            val ambiguous = fixture.economy.requireBridgeReconciliation(
                prepared.id,
                "Provider threw an exception",
            ).appliedValue()

            assertEquals(EconomyBridgeStatus.RECONCILIATION_REQUIRED, ambiguous.status)
            assertEquals(MoneyAmount(400), fixture.balance(fixture.northId))
            assertEquals(ambiguous, fixture.economy.requireBridgeReconciliation(
                prepared.id,
                "duplicate callback",
            ).unchangedValue())
        }
    }

    @Test
    fun `successful reconciliation applies the correct side exactly once`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()
            val deposit = fixture.economy.preparePlayerDeposit(
                fixture.playerTransfer("deposit:reconcile", MoneyAmount(250)),
            ).appliedValue()
            fixture.economy.recoverInterruptedBridgeTransfers()

            val credited = fixture.economy.reconcileBridgeTransfer(
                ReconcileEconomyBridgeTransfer(
                    deposit.id,
                    externalOperationSucceeded = true,
                    adminPlayerId = null,
                    reason = "Provider history confirms player debit",
                ),
            ).appliedValue()
            assertEquals(EconomyBridgeStatus.COMPLETED, credited.status)
            assertEquals(MoneyAmount(750), fixture.balance(fixture.northId))

            val withdrawal = fixture.economy.preparePlayerWithdrawal(
                fixture.playerTransfer("withdraw:reconcile", MoneyAmount(100)),
            ).appliedValue()
            assertEquals(MoneyAmount(650), fixture.balance(fixture.northId))
            fixture.economy.recoverInterruptedBridgeTransfers()

            val retained = fixture.economy.reconcileBridgeTransfer(
                ReconcileEconomyBridgeTransfer(
                    withdrawal.id,
                    externalOperationSucceeded = true,
                    adminPlayerId = null,
                    reason = "Provider history confirms player credit",
                ),
            ).appliedValue()
            assertEquals(EconomyBridgeStatus.COMPLETED, retained.status)
            assertEquals(MoneyAmount(650), fixture.balance(fixture.northId))
        }
    }

    @Test
    fun `withdrawals require leader role and sufficient treasury funds`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            fixture.economy.ensureSeasonAccounts(fixture.seasonId).appliedValue()

            assertIs<EconomyWithdrawalRequiresLeader>(
                fixture.economy.preparePlayerWithdrawal(
                    fixture.playerTransfer(
                        key = "withdraw:member",
                        amount = MoneyAmount(100),
                        playerNumber = 2,
                    ),
                ).rejection(),
            )
            assertIs<InsufficientCivilizationFunds>(
                fixture.economy.preparePlayerWithdrawal(
                    fixture.playerTransfer("withdraw:large", MoneyAmount(501)),
                ).rejection(),
            )
        }
    }

    private fun fixture(database: SqliteTestDatabase): Fixture {
        database.migrator.migrate()
        val ids = SequentialIdGenerator()
        val season = SeasonService(database.repository, ids, clock).create("Season One").appliedValue()
        val civilizations = CivilizationService(database.repository, ids, clock)
        val north = civilizations.provision(
            ProvisionCivilization(season.id, "North", playerId(1), setOf(playerId(2))),
        ).appliedValue().civilization
        val south = civilizations.provision(
            ProvisionCivilization(season.id, "South", playerId(3)),
        ).appliedValue().civilization
        return Fixture(
            database = database,
            economy = EconomyService(
                database.repository,
                ids,
                clock,
                EconomyRules(
                    currencyScale = CurrencyScale(2),
                    openingCivilizationBalance = MoneyAmount(500),
                    repair = RepairEconomyRules(
                        restoreOriginalUnitPrice = MoneyAmount(100),
                        removePlacementUnitPrice = MoneyAmount(100),
                        victorShareBasisPoints = 2_500,
                        allowDebt = false,
                        ordinaryInitiatorRoles = setOf(MembershipRole.LEADER),
                    ),
                ),
            ),
            seasonId = season.id,
            northId = north.id,
            southId = south.id,
        )
    }

    private data class Fixture(
        val database: SqliteTestDatabase,
        val economy: EconomyService,
        val seasonId: SeasonId,
        val northId: CivilizationId,
        val southId: CivilizationId,
    ) {
        fun balance(civilizationId: CivilizationId): MoneyAmount =
            checkNotNull(database.repository.read { findCivilizationAccount(civilizationId) }).balance

        fun playerTransfer(
            key: String,
            amount: MoneyAmount,
            playerNumber: Long = 1,
        ) = PreparePlayerEconomyTransfer(
            seasonId = seasonId,
            civilizationId = northId,
            playerId = playerId(playerNumber),
            amount = amount,
            providerName = "TestEconomy",
            providerFractionalDigits = 2,
            idempotencyKey = key,
        )
    }
}
