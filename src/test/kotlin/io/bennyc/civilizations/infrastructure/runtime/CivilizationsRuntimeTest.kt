package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.RepairEconomyRules
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.damage.PrepareBlockMutation
import io.bennyc.civilizations.application.damage.PreparedBlockMutation
import io.bennyc.civilizations.application.protection.PlayerProtectionAction
import io.bennyc.civilizations.application.protection.PlayerProtectionRequest
import io.bennyc.civilizations.application.protection.ProtectionDecision
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.application.war.DeclareWar
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.JdbcCivilizationsRepository
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SchemaMigrator
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteConnectionFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CivilizationsRuntimeTest {
    private val instant = Instant.parse("2026-08-18T12:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    private val directExecutor = Executor(Runnable::run)
    private val world = WorldId("minecraft:overworld")

    @Test
    fun `serialized mutations publish copy-on-write state and survive restart`() {
        RuntimeDatabase().use { database ->
            val runtime = database.runtime()
            val started = runtime.startAwait()
            assertEquals(7, started.migration.currentVersion)
            assertEquals(null, started.state.activeSeason)

            val seasonFuture = runtime.submitAwait {
                seasons.create("Season One")
            }
            val civilizationFuture = runtime.submitAwait {
                val seasonId = assertNotNull(activeSeasonId())
                civilizations.provision(
                    ProvisionCivilization(
                        seasonId = seasonId,
                        rawName = "North",
                        leaderId = playerId(1),
                        memberIds = setOf(playerId(2)),
                    ),
                )
            }

            val seasonResult = seasonFuture.awaitCompleted()
            val civilizationResult = civilizationFuture.awaitCompleted()
            assertIs<ApplicationResult.Applied<*>>(seasonResult.result)
            val roster = assertIs<ApplicationResult.Applied<*>>(civilizationResult.result).value
                as io.bennyc.civilizations.application.civilization.CivilizationRoster
            assertEquals(1, civilizationResult.state.activeSeason?.civilizations?.size)
            val beforeClaimIndex = assertNotNull(
                civilizationResult.state.activeSeason?.claimIndex,
            )

            val claimResult = runtime.submitAwait {
                claims.place(
                    PlaceClaim(
                        civilizationId = roster.civilization.id,
                        bounds = ClaimBounds.between(world, -16, -16, -1, -1),
                    ),
                )
            }.awaitCompleted()
            assertIs<ApplicationResult.Applied<*>>(claimResult.result)
            val ready = assertIs<CivilizationsRuntimeState.Ready>(runtime.state)
            assertEquals(0, beforeClaimIndex.size)
            assertEquals(1, ready.activeSeason?.claimIndex?.size)
            assertEquals(
                roster.civilization.id,
                ready.activeSeason?.claimIndex
                    ?.claimAt(BlockPosition2D(world, -1, -1))
                    ?.civilizationId,
            )
            val protectedPosition = BlockPosition2D(world, -1, -1)
            assertIs<ProtectionDecision.Allowed>(
                ready.activeSeason?.protection?.decidePlayerAction(
                    PlayerProtectionRequest(
                        actorId = playerId(1),
                        action = PlayerProtectionAction.BLOCK_BREAK,
                        target = protectedPosition,
                    ),
                ),
            )
            assertIs<ProtectionDecision.Denied>(
                ready.activeSeason?.protection?.decidePlayerAction(
                    PlayerProtectionRequest(
                        actorId = playerId(99),
                        action = PlayerProtectionAction.BLOCK_BREAK,
                        target = protectedPosition,
                    ),
                ),
            )
            runtime.close()

            val restarted = database.runtime()
            val recovered = restarted.startAwait().state
            assertEquals("Season One", recovered.activeSeason?.season?.name)
            assertEquals(1, recovered.activeSeason?.civilizations?.size)
            assertEquals(1, recovered.activeSeason?.claimIndex?.size)
            assertIs<ProtectionDecision.Allowed>(
                recovered.activeSeason?.protection?.decidePlayerAction(
                    PlayerProtectionRequest(
                        actorId = playerId(2),
                        action = PlayerProtectionAction.CONTAINER_ACCESS,
                        target = BlockPosition2D(world, -16, -16),
                    ),
                ),
            )
            restarted.close()
        }
    }

    @Test
    fun `active battle eligibility survives restart and expiry recovers fail closed`() {
        RuntimeDatabase().use { database ->
            val mutableClock = MutableClock(instant)
            val runtime = database.runtime(clock = mutableClock)
            runtime.startAwait()

            val season = runtime.submitAwait { seasons.create("Season One") }
                .awaitCompleted()
                .appliedValue()
            val north = runtime.submitAwait {
                civilizations.provision(
                    ProvisionCivilization(
                        seasonId = season.id,
                        rawName = "North",
                        leaderId = playerId(1),
                        memberIds = setOf(playerId(2)),
                    ),
                )
            }.awaitCompleted().appliedValue()
            val south = runtime.submitAwait {
                civilizations.provision(
                    ProvisionCivilization(
                        seasonId = season.id,
                        rawName = "South",
                        leaderId = playerId(3),
                        memberIds = setOf(playerId(4)),
                    ),
                )
            }.awaitCompleted().appliedValue()
            val northClaim = runtime.submitAwait {
                claims.place(
                    PlaceClaim(
                        north.civilization.id,
                        ClaimBounds.between(world, 0, 0, 15, 15),
                    ),
                )
            }.awaitCompleted().appliedValue()
            val southClaim = runtime.submitAwait {
                claims.place(
                    PlaceClaim(
                        south.civilization.id,
                        ClaimBounds.between(world, 32, 0, 47, 15),
                    ),
                )
            }.awaitCompleted().appliedValue()
            runtime.submitAwait { seasons.transition(season.id, SeasonStatus.PEACE) }
                .awaitCompleted()
            runtime.submitAwait { seasons.transition(season.id, SeasonStatus.WAR) }
                .awaitCompleted()
            val war = runtime.submitAwait {
                wars.declare(
                    DeclareWar(
                        seasonId = season.id,
                        declaringCivilizationId = north.civilization.id,
                        targetCivilizationId = south.civilization.id,
                        declaredByPlayerId = playerId(1),
                        battleDurationSeconds = 60,
                    ),
                )
            }.awaitCompleted().let { outcome ->
                val declared = outcome.appliedValue()
                val live = assertNotNull(outcome.state.activeSeason)
                val entry = assertNotNull(
                    live.hostileClaimEntry(
                        playerId(2),
                        BlockPosition2D(world, 40, 8),
                    ),
                )
                assertEquals(declared.id, entry.war.id)
                assertEquals(southClaim.id, entry.enteredClaim.id)
                assertTrue(entry.battlePhaseOpen)
                assertNull(entry.existingOpenBattle)
                assertNull(
                    live.hostileClaimEntry(
                        playerId(2),
                        BlockPosition2D(world, 8, 8),
                    ),
                )
                declared
            }
            val battleOutcome = runtime.submitAwait {
                wars.startBattleFromEntry(war.id, playerId(2), southClaim.id)
            }.awaitCompleted()
            val battle = battleOutcome.appliedValue().battle
            val live = assertNotNull(battleOutcome.state.activeSeason)
            val eligibility = live.activeBattleEligibility.single()
            assertEquals(
                setOf(southClaim.id),
                eligibility.opposingClaimIdsByCivilization[north.civilization.id],
            )
            assertEquals(
                setOf(northClaim.id),
                eligibility.opposingClaimIdsByCivilization[south.civilization.id],
            )
            assertEquals(
                battle.id,
                live.hostileClaimEntry(
                    playerId(2),
                    BlockPosition2D(world, 40, 8),
                )?.existingOpenBattle?.id,
            )
            assertNotNull(
                live.authorizeBattleBlockMutation(
                    playerId(2),
                    PlayerProtectionAction.BLOCK_BREAK,
                    BlockPosition2D(world, 40, 8),
                ),
            )
            assertNotNull(
                live.authorizeBattleBlockMutation(
                    playerId(3),
                    PlayerProtectionAction.BLOCK_PLACE,
                    BlockPosition2D(world, 40, 8),
                ),
                "Owner changes in battle land must also enter the journal bridge",
            )
            assertNull(
                live.authorizeBattleBlockMutation(
                    playerId(99),
                    PlayerProtectionAction.BLOCK_BREAK,
                    BlockPosition2D(world, 40, 8),
                ),
            )
            assertNull(
                live.authorizeBattleBlockMutation(
                    playerId(2),
                    PlayerProtectionAction.BLOCK_BREAK,
                    BlockPosition2D(world, 80, 8),
                ),
            )
            assertIs<ProtectionDecision.Denied>(
                live.protection.decidePlayerAction(
                    PlayerProtectionRequest(
                        actorId = playerId(1),
                        action = PlayerProtectionAction.BLOCK_BREAK,
                        target = BlockPosition2D(world, 32, 0),
                    ),
                ),
            )
            val moved = runtime.submitAwait {
                civilizations.moveMember(
                    season.id,
                    playerId(2),
                    south.civilization.id,
                )
            }.awaitCompleted()
            val movedLive = assertNotNull(moved.state.activeSeason)
            assertEquals(
                south.civilization.id,
                movedLive.membershipOf(playerId(2))?.civilizationId,
            )
            assertEquals(
                north.civilization.id,
                movedLive.activeBattleEligibility.single().participants
                    .single { it.playerId == playerId(2) }
                    .civilizationId,
                "Political roster changes must not rewrite active battle sides",
            )
            assertEquals(
                north.civilization.id,
                assertNotNull(
                    movedLive.authorizeBattleBlockMutation(
                        playerId(2),
                        PlayerProtectionAction.BLOCK_BREAK,
                        BlockPosition2D(world, 40, 8),
                    ),
                ).actorCivilizationId,
            )
            val stateBeforeJournal = runtime.state
            val journaled = runtime.prepareBlockMutationAwait(
                PrepareBlockMutation(
                    battleId = battle.id,
                    claimId = southClaim.id,
                    position = BlockPosition3D(world, 40, 72, 8),
                    observedState = SimpleBlockSnapshot("minecraft:stone"),
                    actorId = playerId(2),
                    cause = BlockMutationCause.PLAYER_BREAK,
                ),
            ).awaitCompleted()
            assertIs<ApplicationResult.Applied<*>>(journaled.result)
            assertSame(
                stateBeforeJournal,
                runtime.state,
                "Journal-only writes must not rebuild the full gameplay snapshot",
            )
            runtime.close()

            val restarted = database.runtime(clock = mutableClock)
            assertEquals(
                BattleStatus.ACTIVE,
                restarted.startAwait().state.activeSeason?.battles?.single()?.status,
            )
            assertEquals(
                1,
                (restarted.state as CivilizationsRuntimeState.Ready)
                    .activeSeason?.activeBattleEligibility?.size,
            )
            val repeated = restarted.prepareBlockMutationAwait(
                PrepareBlockMutation(
                    battleId = battle.id,
                    claimId = southClaim.id,
                    position = BlockPosition3D(world, 40, 72, 8),
                    observedState = SimpleBlockSnapshot("minecraft:air"),
                    actorId = playerId(2),
                    cause = BlockMutationCause.PLAYER_PLACE,
                ),
            ).awaitCompleted()
            assertIs<ApplicationResult.Unchanged<*>>(repeated.result)
            restarted.close()

            mutableClock.advanceSeconds(60)
            val expiredRestart = database.runtime(clock = mutableClock)
            val recovered = assertNotNull(expiredRestart.startAwait().state.activeSeason)
            assertEquals(BattleStatus.RESOLVING, recovered.battles.single().status)
            assertEquals(battle.endsAt, recovered.battles.single().resolvingAt)
            assertTrue(recovered.activeBattleEligibility.isEmpty())
            expiredRestart.close()
        }
    }

    @Test
    fun `startup fails closed when active durable state violates invariants`() {
        RuntimeDatabase().use { database ->
            val connectionFactory = SqliteConnectionFactory(database.path)
            SchemaMigrator(connectionFactory, clock = clock).migrate()
            val repository = JdbcCivilizationsRepository(connectionFactory)
            val season = Season(
                id = SeasonId(UUID(0, 1)),
                name = "Corrupt Season",
                status = SeasonStatus.PEACE,
                createdAt = instant,
                updatedAt = instant,
            )
            val leaderlessActiveCivilization = Civilization(
                id = CivilizationId(UUID(0, 2)),
                seasonId = season.id,
                name = CivilizationName.from("Leaderless"),
                status = CivilizationStatus.ACTIVE,
                createdAt = instant,
                updatedAt = instant,
            )
            repository.transaction {
                insertSeason(season)
                insertCivilization(leaderlessActiveCivilization)
                setActiveSeasonId(season.id)
            }

            val fatal = CompletableFuture<Throwable>()
            val runtime = database.runtime(fatalFailureHandler = fatal::complete)
            val outcomeFuture = CompletableFuture<RuntimeStartOutcome>()
            runtime.start(outcomeFuture::complete)

            val outcome = outcomeFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val failure = assertIs<RuntimeStartOutcome.Failed>(outcome).failure
            assertIs<RuntimeIntegrityException>(failure)
            assertTrue(failure.message!!.contains("has 0 leaders"))
            assertEquals(failure, fatal.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertIs<CivilizationsRuntimeState.Failed>(runtime.state)
            runtime.close()
        }
    }

    @Test
    fun `mutations reject before startup without touching storage`() {
        RuntimeDatabase().use { database ->
            val runtime = database.runtime()
            val outcome = runtime.submitAwait {
                seasons.create("Must Not Exist")
            }.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertIs<RuntimeMutationOutcome.NotReady>(outcome)
            assertEquals(null, runtime.startAwait().state.activeSeason)
            runtime.close()
        }
    }

    private fun RuntimeDatabase.runtime(
        fatalFailureHandler: (Throwable) -> Unit = {},
        clock: Clock = this@CivilizationsRuntimeTest.clock,
    ): CivilizationsRuntime = CivilizationsRuntime.sqlite(
        databasePath = path,
        claimRules = ClaimRules(
            maxArea = 256,
            maxClaimsPerCivilization = 4,
        ),
        economyRules = EconomyRules(
            currencyScale = CurrencyScale(2),
            openingCivilizationBalance = MoneyAmount.ZERO,
            repair = RepairEconomyRules(
                restoreOriginalUnitPrice = MoneyAmount(100),
                removePlacementUnitPrice = MoneyAmount(100),
                victorShareBasisPoints = 2_500,
                allowDebt = false,
                ordinaryInitiatorRoles = setOf(MembershipRole.LEADER),
            ),
        ),
        serverThread = directExecutor,
        fatalFailureHandler = fatalFailureHandler,
        idGenerator = SequentialIdGenerator(),
        clock = clock,
    )

    private fun CivilizationsRuntime.startAwait(): RuntimeStartOutcome.Ready {
        val future = CompletableFuture<RuntimeStartOutcome>()
        start(future::complete)
        return assertIs(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun <T> CivilizationsRuntime.submitAwait(
        operation: RuntimeMutationScope.() -> ApplicationResult<T>,
    ): CompletableFuture<RuntimeMutationOutcome<T>> =
        CompletableFuture<RuntimeMutationOutcome<T>>().also { future ->
            submitMutation(operation, future::complete)
        }

    private fun CivilizationsRuntime.prepareBlockMutationAwait(
        request: PrepareBlockMutation,
    ): CompletableFuture<RuntimeMutationOutcome<PreparedBlockMutation>> =
        CompletableFuture<RuntimeMutationOutcome<PreparedBlockMutation>>()
            .also { future -> prepareBlockMutation(request, future::complete) }

    private fun <T> CompletableFuture<RuntimeMutationOutcome<T>>.awaitCompleted():
        RuntimeMutationOutcome.Completed<T> =
        assertIs(get(TIMEOUT_SECONDS, TimeUnit.SECONDS))

    private fun <T> RuntimeMutationOutcome.Completed<T>.appliedValue(): T =
        assertIs<ApplicationResult.Applied<T>>(result).value

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }

    private class RuntimeDatabase : AutoCloseable {
        private val directory: Path = Files.createTempDirectory("civilizations-runtime-test-")
        val path: Path = directory.resolve("civilizations-v2.db")

        override fun close() {
            path.resolveSibling("${path.fileName}-wal").deleteIfExists()
            path.resolveSibling("${path.fileName}-shm").deleteIfExists()
            path.deleteIfExists()
            directory.deleteIfExists()
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
