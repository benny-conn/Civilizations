package io.bennyc.civilizations.application.war

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.claim.ClaimService
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.damage.DamageReportService
import io.bennyc.civilizations.application.damage.GenerateDamageReport
import io.bennyc.civilizations.application.economy.BattleCasualtyRules
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.EconomyService
import io.bennyc.civilizations.application.economy.EconomyWithdrawalLockedForBattle
import io.bennyc.civilizations.application.economy.PreparePlayerEconomyTransfer
import io.bennyc.civilizations.application.economy.RepairEconomyRules
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.appliedValue
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.support.rejection
import io.bennyc.civilizations.application.support.unchangedValue
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleCombatResolutionCause
import io.bennyc.civilizations.domain.war.BattleCombatRulesSnapshot
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleLifeEventId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleSide
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteTestDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BattleCombatServiceTest {
    @Test
    fun `battle keeps full roster history but enrolls only selected combatants`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val roster = fixture.startBattle(
                combatantIds = setOf(playerId(2), playerId(3)),
            )

            assertEquals(4, roster.participants.size)
            assertEquals(setOf(playerId(2), playerId(3)), roster.combatants.mapTo(mutableSetOf()) {
                it.playerId
            })
            assertEquals(1, roster.combatState?.rules?.livesPerCombatant)
            assertEquals(BattleSide.ATTACKER, roster.combatants.single {
                it.playerId == playerId(2)
            }.side)
            assertEquals(BattleSide.DEFENDER, roster.combatants.single {
                it.playerId == playerId(3)
            }.side)

            database.repository.read {
                assertEquals(roster.combatState, findBattleCombatState(roster.battle.id))
                assertEquals(roster.combatants.toSet(), listBattleCombatants(roster.battle.id).toSet())
            }
        }
    }

    @Test
    fun `combat enrollment requires trigger and at least one combatant on both sides`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            assertIs<BattleTriggerMustBeCombatant>(
                fixture.startBattleResult(setOf(playerId(1), playerId(3))).rejection(),
            )
            assertIs<BattleCombatantSideEmpty>(
                fixture.startBattleResult(setOf(playerId(2))).rejection(),
            )
            assertTrue(database.repository.read { listBattlesForSeason(fixture.seasonId).isEmpty() })
        }
    }

    @Test
    fun `life loss is idempotent and final defender elimination requests attacker victory`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val battle = fixture.startBattle(
                setOf(playerId(1), playerId(2), playerId(3), playerId(4)),
            ).battle
            val firstLoss = fixture.loss(1, 3)
            val first = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(firstLoss)),
            ).appliedValue()
            assertEquals(BattleStatus.ACTIVE, first.battle.status)
            assertTrue(first.combatants.single { it.playerId == playerId(3) }.isEliminated)

            val retried = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(firstLoss)),
            ).unchangedValue()
            assertEquals(1, retried.lifeEvents.size)
            assertEquals(1, database.repository.read { listBattleLifeEvents(battle.id).size })

            val decided = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(fixture.loss(2, 4))),
            ).appliedValue()
            assertEquals(BattleStatus.RESOLVING, decided.battle.status)
            assertEquals(BattleCombatResolutionCause.ELIMINATION, decided.state.resolutionCause)
            assertEquals(BattleOutcome.ATTACKER_VICTORY, decided.state.requestedOutcome)
            assertEquals(2, database.repository.read { listBattleLifeEvents(battle.id).size })
        }
    }

    @Test
    fun `same-batch final elimination on both sides is a draw`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val battle = fixture.startBattle(setOf(playerId(2), playerId(3))).battle

            val decided = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(
                    battle.id,
                    listOf(fixture.loss(1, 2), fixture.loss(2, 3)),
                ),
            ).appliedValue()

            assertEquals(BattleOutcome.DRAW, decided.state.requestedOutcome)
            assertTrue(decided.combatants.all { it.isEliminated })
            assertEquals(BattleStatus.RESOLVING, decided.battle.status)
        }
    }

    @Test
    fun `snapshotted multiple lives decrement before elimination`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, livesPerCombatant = 2)
            val battle = fixture.startBattle(setOf(playerId(2), playerId(3))).battle

            val wounded = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(fixture.loss(1, 2))),
            ).appliedValue()
            assertEquals(BattleStatus.ACTIVE, wounded.battle.status)
            assertEquals(1, wounded.combatants.single { it.playerId == playerId(2) }.livesRemaining)

            val eliminated = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(fixture.loss(2, 2))),
            ).appliedValue()
            assertEquals(BattleOutcome.DEFENDER_VICTORY, eliminated.state.requestedOutcome)
            assertEquals(BattleStatus.RESOLVING, eliminated.battle.status)
        }
    }

    @Test
    fun `timeout durably requests defender victory and closes through sealed damage`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, durationSeconds = 60)
            val battle = fixture.startBattle(setOf(playerId(2), playerId(3))).battle

            assertIs<BattleHasNotExpired>(
                fixture.combat.beginTimeoutResolution(battle.id).rejection(),
            )
            fixture.clock.advanceSeconds(60)
            val timeout = fixture.combat.recoverExpiredBattles(fixture.seasonId).single()
            assertEquals(BattleStatus.RESOLVING, timeout.battle.status)
            assertEquals(battle.endsAt, timeout.battle.resolvingAt)
            assertEquals(BattleOutcome.DEFENDER_VICTORY, timeout.requestedOutcome)
            assertEquals(BattleCombatResolutionCause.TIMEOUT, timeout.combatState?.resolutionCause)
            assertTrue(fixture.combat.recoverExpiredBattles(fixture.seasonId).isEmpty())

            DamageReportService(database.repository, fixture.clock).generate(
                GenerateDamageReport(battle.id, emptyList()),
            ).appliedValue()
            val closed = fixture.wars.resolve(
                battle.id,
                checkNotNull(timeout.requestedOutcome),
            ).appliedValue()
            assertEquals(BattleOutcome.DEFENDER_VICTORY, closed.outcome)
            assertEquals(fixture.defenderId, closed.winnerCivilizationId)

            val restarted = BattleCombatService(database.repository, fixture.ids, fixture.clock)
            assertTrue(restarted.recoverExpiredBattles(fixture.seasonId).isEmpty())
            val persisted = database.repository.read { findBattleCombatState(battle.id) }
            assertEquals(BattleOutcome.DEFENDER_VICTORY, persisted?.requestedOutcome)
        }
    }

    @Test
    fun `pre-migration style battle without combat enrollment stays outcome-neutral at timeout`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database, durationSeconds = 60)
            val battle = fixture.startLegacyBattle()
            fixture.clock.advanceSeconds(60)

            val timeout = fixture.combat.beginTimeoutResolution(battle).appliedValue()
            assertNull(timeout.combatState)
            assertNull(timeout.requestedOutcome)
            assertEquals(BattleStatus.RESOLVING, timeout.battle.status)
        }
    }

    @Test
    fun `one life event id cannot identify another combatant loss`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(database)
            val battle = fixture.startBattle(
                setOf(playerId(1), playerId(2), playerId(3), playerId(4)),
            ).battle
            fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(fixture.loss(1, 1))),
            ).appliedValue()

            assertIs<BattleLifeEventConflict>(
                fixture.combat.recordLifeLosses(
                    RecordBattleLifeLosses(battle.id, listOf(fixture.loss(1, 3))),
                ).rejection(),
            )
        }
    }

    @Test
    fun `battle start requires and durably reserves maximum attacker casualty coverage`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(
                database,
                casualtyRules = BattleCasualtyRules(
                    attackerDeathCost = MoneyAmount(250),
                    defenderDeathCost = MoneyAmount(100),
                    requireAttackerCoverage = true,
                    lockWithdrawalsDuringBattle = true,
                ),
                openingBalance = MoneyAmount(200),
            )

            val rejected = fixture.startBattleResult(setOf(playerId(2), playerId(3))).rejection()
            assertIs<InsufficientAttackerCasualtyCoverage>(rejected)
            assertTrue(database.repository.read { listBattlesForSeason(fixture.seasonId).isEmpty() })

            fixture.credit(fixture.attackerId, MoneyAmount(50), "coverage-top-up")
            val roster = fixture.startBattle(setOf(playerId(2), playerId(3)))
            val economics = assertNotNull(roster.casualtyEconomics)
            assertEquals(MoneyAmount(250), economics.attackerReserve)
            assertEquals(MoneyAmount.ZERO, fixture.balance(fixture.attackerId))
            assertEquals(
                LedgerTransactionKind.BATTLE_CASUALTY_RESERVE,
                fixture.economy!!.listLedger(fixture.attackerId).first().kind,
            )
        }
    }

    @Test
    fun `casualties charge once without debt and unused attacker coverage releases`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(
                database,
                livesPerCombatant = 2,
                casualtyRules = BattleCasualtyRules(
                    attackerDeathCost = MoneyAmount(250),
                    defenderDeathCost = MoneyAmount(100),
                    requireAttackerCoverage = true,
                    lockWithdrawalsDuringBattle = true,
                ),
                openingBalance = MoneyAmount(500),
            )
            val battle = fixture.startBattle(setOf(playerId(2), playerId(3))).battle
            assertEquals(MoneyAmount.ZERO, fixture.balance(fixture.attackerId))

            val attackerLoss = fixture.loss(1, 2)
            val first = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(attackerLoss)),
            ).appliedValue()
            val casualty = first.casualties.single()
            assertEquals(MoneyAmount(250), casualty.chargedAmount)
            assertEquals(MoneyAmount.ZERO, casualty.unpaidAmount)
            BattleCombatService(database.repository, fixture.ids, fixture.clock).recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(attackerLoss)),
            ).unchangedValue()
            assertEquals(1, database.repository.read { listBattleCasualties(battle.id).size })

            fixture.wars.cancelBattle(battle.id).appliedValue()
            assertEquals(MoneyAmount(250), fixture.balance(fixture.attackerId))
            val released = assertNotNull(
                database.repository.read { findBattleCasualtyEconomics(battle.id) },
            )
            assertEquals(MoneyAmount(250), released.releasedAmount)
            assertEquals(
                listOf(
                    LedgerTransactionKind.BATTLE_CASUALTY_RELEASE,
                    LedgerTransactionKind.BATTLE_CASUALTY_RESERVE,
                    LedgerTransactionKind.OPENING_BALANCE,
                ),
                fixture.economy!!.listLedger(fixture.attackerId).map { it.kind },
            )
        }
    }

    @Test
    fun `defender casualty stops at zero records unpaid amount and locks withdrawals`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(
                database,
                casualtyRules = BattleCasualtyRules(
                    attackerDeathCost = MoneyAmount.ZERO,
                    defenderDeathCost = MoneyAmount(100),
                    requireAttackerCoverage = true,
                    lockWithdrawalsDuringBattle = true,
                ),
                openingBalance = MoneyAmount(50),
            )
            val battle = fixture.startBattle(setOf(playerId(2), playerId(3))).battle

            assertIs<EconomyWithdrawalLockedForBattle>(
                fixture.economy!!.preparePlayerWithdrawal(
                    PreparePlayerEconomyTransfer(
                        seasonId = fixture.seasonId,
                        civilizationId = fixture.defenderId,
                        playerId = playerId(3),
                        amount = MoneyAmount(1),
                        providerName = "TestEconomy",
                        providerFractionalDigits = 2,
                        idempotencyKey = "withdraw-during-battle",
                    ),
                ).rejection(),
            )

            val update = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(fixture.loss(1, 3))),
            ).appliedValue()
            val casualty = update.casualties.single()
            assertEquals(MoneyAmount(50), casualty.chargedAmount)
            assertEquals(MoneyAmount(50), casualty.unpaidAmount)
            assertEquals(MoneyAmount.ZERO, fixture.balance(fixture.defenderId))
            assertIs<EconomyWithdrawalLockedForBattle>(
                fixture.economy.preparePlayerWithdrawal(
                    PreparePlayerEconomyTransfer(
                        seasonId = fixture.seasonId,
                        civilizationId = fixture.defenderId,
                        playerId = playerId(3),
                        amount = MoneyAmount(1),
                        providerName = "TestEconomy",
                        providerFractionalDigits = 2,
                        idempotencyKey = "withdraw-while-resolving",
                    ),
                ).rejection(),
            )
        }
    }

    @Test
    fun `coverage and withdrawal lock can be disabled without creating debt`() {
        SqliteTestDatabase().use { database ->
            val fixture = fixture(
                database,
                casualtyRules = BattleCasualtyRules(
                    attackerDeathCost = MoneyAmount(100),
                    defenderDeathCost = MoneyAmount(25),
                    requireAttackerCoverage = false,
                    lockWithdrawalsDuringBattle = false,
                ),
                openingBalance = MoneyAmount(50),
            )
            val battle = fixture.startBattle(setOf(playerId(2), playerId(3))).battle
            val casualty = fixture.combat.recordLifeLosses(
                RecordBattleLifeLosses(battle.id, listOf(fixture.loss(1, 2))),
            ).appliedValue().casualties.single()
            assertEquals(MoneyAmount(50), casualty.chargedAmount)
            assertEquals(MoneyAmount(50), casualty.unpaidAmount)
            assertEquals(MoneyAmount.ZERO, fixture.balance(fixture.attackerId))

            DamageReportService(database.repository, fixture.clock).generate(
                GenerateDamageReport(battle.id, emptyList()),
            ).appliedValue()
            fixture.wars.resolve(battle.id, BattleOutcome.DEFENDER_VICTORY).appliedValue()
            val economics = assertNotNull(
                database.repository.read { findBattleCasualtyEconomics(battle.id) },
            )
            assertEquals(MoneyAmount.ZERO, economics.attackerReserve)
            assertEquals(MoneyAmount.ZERO, economics.releasedAmount)
        }
    }

    private fun fixture(
        database: SqliteTestDatabase,
        durationSeconds: Long = 600,
        livesPerCombatant: Int = 1,
        casualtyRules: BattleCasualtyRules? = null,
        openingBalance: MoneyAmount = MoneyAmount.ZERO,
    ): Fixture {
        database.migrator.migrate()
        val clock = MutableClock(Instant.parse("2026-08-28T12:00:00Z"))
        val ids = SequentialIdGenerator()
        val seasons = SeasonService(database.repository, ids, clock)
        val season = seasons.create("Season One").appliedValue()
        val civilizations = CivilizationService(database.repository, ids, clock)
        val attacker = civilizations.provision(
            ProvisionCivilization(
                season.id,
                "North",
                playerId(1),
                memberIds = setOf(playerId(2)),
            ),
        ).appliedValue().civilization
        val defender = civilizations.provision(
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
            PlaceClaim(
                attacker.id,
                ClaimBounds.between(WorldId("minecraft:overworld"), 0, 0, 15, 15),
            ),
        ).appliedValue()
        val defenderClaim = claims.place(
            PlaceClaim(
                defender.id,
                ClaimBounds.between(WorldId("minecraft:overworld"), 32, 0, 47, 15),
            ),
        ).appliedValue().id
        seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
        seasons.transition(season.id, SeasonStatus.WAR).appliedValue()
        val economy = casualtyRules?.let {
            EconomyService(
                database.repository,
                ids,
                clock,
                EconomyRules(
                    currencyScale = CurrencyScale(0),
                    openingCivilizationBalance = openingBalance,
                    repair = RepairEconomyRules(
                        restoreOriginalUnitPrice = MoneyAmount.ZERO,
                        removePlacementUnitPrice = MoneyAmount.ZERO,
                        victorShareBasisPoints = 0,
                        ordinaryInitiatorRoles = setOf(MembershipRole.LEADER),
                    ),
                    battleCasualties = it,
                ),
            ).also { service -> service.ensureSeasonAccounts(season.id).appliedValue() }
        }
        val wars = WarService(database.repository, ids, clock, casualtyRules)
        val war = wars.declare(
            DeclareWar(
                season.id,
                attacker.id,
                defender.id,
                playerId(1),
                durationSeconds,
            ),
        ).appliedValue()
        return Fixture(
            database = database,
            clock = clock,
            ids = ids,
            wars = wars,
            combat = BattleCombatService(database.repository, ids, clock),
            economy = economy,
            seasonId = season.id,
            attackerId = attacker.id,
            defenderId = defender.id,
            warId = war.id,
            defenderClaimId = defenderClaim,
            rules = BattleCombatRulesSnapshot(livesPerCombatant),
        )
    }

    private data class Fixture(
        val database: SqliteTestDatabase,
        val clock: MutableClock,
        val ids: SequentialIdGenerator,
        val wars: WarService,
        val combat: BattleCombatService,
        val economy: EconomyService?,
        val seasonId: SeasonId,
        val attackerId: CivilizationId,
        val defenderId: CivilizationId,
        val warId: io.bennyc.civilizations.domain.war.WarId,
        val defenderClaimId: ClaimId,
        val rules: BattleCombatRulesSnapshot,
    ) {
        fun startBattleResult(combatantIds: Set<io.bennyc.civilizations.domain.identity.PlayerId>) =
            wars.startBattleFromEntry(
                warId,
                playerId(2),
                defenderClaimId,
                BattleCombatEnrollment(rules, combatantIds),
            )

        fun startBattle(combatantIds: Set<io.bennyc.civilizations.domain.identity.PlayerId>) =
            startBattleResult(combatantIds).appliedValue()

        fun startLegacyBattle(): BattleId = wars.startBattleFromEntry(
            warId,
            playerId(2),
            defenderClaimId,
        ).appliedValue().battle.id

        fun loss(event: Long, player: Long): BattleLifeLoss = BattleLifeLoss(
            BattleLifeEventId(UUID(2, event)),
            playerId(player),
        )

        fun balance(civilizationId: CivilizationId): MoneyAmount =
            checkNotNull(database.repository.read { findCivilizationAccount(civilizationId) }).balance

        fun credit(civilizationId: CivilizationId, amount: MoneyAmount, key: String) {
            checkNotNull(economy).post(
                io.bennyc.civilizations.application.economy.LedgerTransactionRequest(
                    seasonId = seasonId,
                    idempotencyKey = key,
                    kind = LedgerTransactionKind.ADMIN_ADJUSTMENT,
                    postings = listOf(
                        io.bennyc.civilizations.domain.economy.LedgerPosting(
                            civilizationId,
                            amount,
                        ),
                    ),
                    referenceType = "TEST",
                    referenceId = key,
                    actorPlayerId = null,
                    description = "Test credit",
                ),
            ).appliedValue()
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
