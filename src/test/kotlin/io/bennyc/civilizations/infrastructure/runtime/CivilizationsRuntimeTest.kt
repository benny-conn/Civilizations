package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.protection.PlayerProtectionAction
import io.bennyc.civilizations.application.protection.PlayerProtectionRequest
import io.bennyc.civilizations.application.protection.ProtectionDecision
import io.bennyc.civilizations.application.support.SequentialIdGenerator
import io.bennyc.civilizations.application.support.playerId
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.season.SeasonStatus
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
            assertEquals(2, started.migration.currentVersion)
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
    ): CivilizationsRuntime = CivilizationsRuntime.sqlite(
        databasePath = path,
        claimRules = ClaimRules(
            maxArea = 256,
            maxClaimsPerCivilization = 4,
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

    private fun <T> CompletableFuture<RuntimeMutationOutcome<T>>.awaitCompleted():
        RuntimeMutationOutcome.Completed<T> =
        assertIs(get(TIMEOUT_SECONDS, TimeUnit.SECONDS))

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
