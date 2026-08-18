package io.bennyc.civilizations.infrastructure.runtime

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationService
import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.claim.ClaimService
import io.bennyc.civilizations.application.claim.ClaimSpatialIndex
import io.bennyc.civilizations.application.damage.DamageJournalService
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.protection.ProtectionService
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.application.war.WarService
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimId
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
import io.bennyc.civilizations.domain.war.WarStatus
import io.bennyc.civilizations.infrastructure.identity.UuidCivilizationsIdGenerator
import io.bennyc.civilizations.infrastructure.persistence.jdbc.JdbcCivilizationsRepository
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SchemaMigrationReport
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SchemaMigrator
import io.bennyc.civilizations.infrastructure.persistence.jdbc.SqliteConnectionFactory
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns persistence work and publishes immutable/copy-on-write runtime state.
 * Every mutation is serialized on one storage thread. Refreshed state is then
 * installed by the supplied server-thread executor in the same order.
 */
class CivilizationsRuntime private constructor(
    private val repository: CivilizationsRepository,
    private val migrator: SchemaMigrator,
    claimRules: ClaimRules,
    private val phaseRules: GameplayPhaseRules,
    idGenerator: CivilizationsIdGenerator,
    clock: Clock,
    private val serverThread: Executor,
    private val worker: ExecutorService,
    private val fatalFailureHandler: (Throwable) -> Unit,
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val mutationScope = RuntimeMutationScope(
        repository = repository,
        seasons = SeasonService(repository, idGenerator, clock),
        civilizations = CivilizationService(repository, idGenerator, clock, phaseRules),
        claims = ClaimService(repository, idGenerator, claimRules, phaseRules),
        wars = WarService(repository, idGenerator, clock),
        damageJournal = DamageJournalService(repository, idGenerator, clock),
    )

    @Volatile
    var state: CivilizationsRuntimeState = CivilizationsRuntimeState.Stopped
        private set

    fun start(completion: (RuntimeStartOutcome) -> Unit = {}) {
        check(started.compareAndSet(false, true)) { "Civilizations runtime has already started" }
        check(!closed.get()) { "Civilizations runtime is closed" }
        state = CivilizationsRuntimeState.Starting

        worker.execute {
            try {
                val migration = migrator.migrate()
                val ready = loadReadyState()
                dispatchToServer {
                    state = ready
                    completion(RuntimeStartOutcome.Ready(migration, ready))
                }
            } catch (failure: Throwable) {
                publishFatal(failure) {
                    completion(RuntimeStartOutcome.Failed(failure))
                }
            }
        }
    }

    fun <T> submitMutation(
        operation: RuntimeMutationScope.() -> ApplicationResult<T>,
        completion: (RuntimeMutationOutcome<T>) -> Unit,
    ) {
        val current = state
        if (closed.get() || current !is CivilizationsRuntimeState.Ready) {
            dispatchToServer {
                completion(RuntimeMutationOutcome.NotReady(state))
            }
            return
        }

        try {
            worker.execute {
                try {
                    val result = mutationScope.operation()
                    val refreshed = loadReadyState()
                    dispatchToServer {
                        state = refreshed
                        completion(RuntimeMutationOutcome.Completed(result, refreshed))
                    }
                } catch (failure: Throwable) {
                    publishFatal(failure) {
                        completion(RuntimeMutationOutcome.Failed(failure))
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            dispatchToServer {
                completion(RuntimeMutationOutcome.NotReady(state))
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        worker.shutdown()
        try {
            if (!worker.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                worker.shutdownNow()
                worker.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        } catch (_: InterruptedException) {
            worker.shutdownNow()
            Thread.currentThread().interrupt()
        }
        state = CivilizationsRuntimeState.Stopped
    }

    private fun loadReadyState(): CivilizationsRuntimeState.Ready {
        repository.read { findActiveSeasonId() }?.let(mutationScope.wars::recoverExpiredBattles)
        val loaded = repository.read {
            val activeSeasonId = findActiveSeasonId()
                ?: return@read LoadedActiveSeason.None
            val season = findSeason(activeSeasonId)
                ?: throw RuntimeIntegrityException(
                    "Runtime state references missing active season $activeSeasonId",
                )
            val civilizations = listCivilizations(activeSeasonId)
            val battles = listBattlesForSeason(activeSeasonId)
            LoadedActiveSeason.Present(
                season = season,
                civilizations = civilizations,
                memberships = civilizations.associate { civilization ->
                    civilization.id to listMemberships(civilization.id)
                },
                claims = listClaimsForSeason(activeSeasonId),
                wars = listWarsForSeason(activeSeasonId),
                battles = battles,
                battleParticipants = battles.associate { battle ->
                    battle.id to listBattleParticipants(battle.id)
                },
            )
        }

        return when (loaded) {
            LoadedActiveSeason.None -> CivilizationsRuntimeState.Ready(activeSeason = null)
            is LoadedActiveSeason.Present -> {
                validate(loaded)
                val index = ClaimSpatialIndex(loaded.season.id, loaded.claims)
                validateNoOverlaps(loaded.claims, index)
                validateWarsAndBattles(loaded)
                val protection = ProtectionService(
                    seasonStatus = loaded.season.status,
                    claimIndex = index,
                    memberships = loaded.memberships.values.flatten(),
                    phaseRules = phaseRules,
                )
                CivilizationsRuntimeState.Ready(
                    activeSeason = ActiveSeasonRuntimeState(
                        season = loaded.season,
                        civilizations = loaded.civilizations,
                        memberships = loaded.memberships,
                        claimIndex = index,
                        protection = protection,
                        wars = loaded.wars,
                        battles = loaded.battles,
                        battleParticipants = loaded.battleParticipants,
                        activeBattleEligibility = buildActiveBattleEligibility(loaded),
                    ),
                )
            }
        }
    }

    private fun validate(loaded: LoadedActiveSeason.Present) {
        if (loaded.season.status == SeasonStatus.ARCHIVED) {
            throw RuntimeIntegrityException(
                "Archived season ${loaded.season.id} is selected as active",
            )
        }
        val civilizationsById = loaded.civilizations.associateBy { it.id }
        for (civilization in loaded.civilizations) {
            val memberships = loaded.memberships.getValue(civilization.id)
            val leaderCount = memberships.count { it.role == MembershipRole.LEADER }
            if (civilization.status == CivilizationStatus.ACTIVE && leaderCount != 1) {
                throw RuntimeIntegrityException(
                    "Active civilization ${civilization.id} has $leaderCount leaders",
                )
            }
        }
        for (claim in loaded.claims) {
            val owner = civilizationsById[claim.civilizationId]
                ?: throw RuntimeIntegrityException(
                    "Claim ${claim.id} references missing civilization ${claim.civilizationId}",
                )
            if (owner.status != CivilizationStatus.ACTIVE) {
                throw RuntimeIntegrityException(
                    "Claim ${claim.id} belongs to ${owner.status} civilization ${owner.id}",
                )
            }
        }
    }

    private fun validateNoOverlaps(
        claims: List<Claim>,
        index: ClaimSpatialIndex,
    ) {
        for (claim in claims) {
            val conflict = index.findIntersecting(claim.bounds).firstOrNull { it.id != claim.id }
            if (conflict != null) {
                throw RuntimeIntegrityException(
                    "Claims ${claim.id} and ${conflict.id} overlap in season ${claim.seasonId}",
                )
            }
        }
    }

    private fun validateWarsAndBattles(loaded: LoadedActiveSeason.Present) {
        val civilizationsById = loaded.civilizations.associateBy(Civilization::id)
        val claimsById = loaded.claims.associateBy(Claim::id)
        val warsById = loaded.wars.associateBy(War::id)
        val openBattleParticipations = mutableMapOf<CivilizationId, Battle>()

        val openWarPairs = mutableMapOf<Set<CivilizationId>, War>()
        for (war in loaded.wars) {
            if (war.seasonId != loaded.season.id) {
                throw RuntimeIntegrityException(
                    "War ${war.id} belongs to season ${war.seasonId}, not ${loaded.season.id}",
                )
            }
            if (war.status == WarStatus.DECLARED || war.status == WarStatus.ACTIVE) {
                for (civilizationId in war.civilizationIds) {
                    val civilization = civilizationsById[civilizationId]
                        ?: throw RuntimeIntegrityException(
                            "Open war ${war.id} references missing civilization $civilizationId",
                        )
                    if (civilization.status != CivilizationStatus.ACTIVE) {
                        throw RuntimeIntegrityException(
                            "Open war ${war.id} references ${civilization.status} " +
                                "civilization $civilizationId",
                        )
                    }
                }
                openWarPairs.put(war.civilizationIds, war)?.let { existing ->
                    throw RuntimeIntegrityException(
                        "Civilizations ${war.civilizationIds} have duplicate open wars " +
                            "${existing.id} and ${war.id}",
                    )
                }
            }
        }

        for (battle in loaded.battles) {
            if (battle.seasonId != loaded.season.id) {
                throw RuntimeIntegrityException(
                    "Battle ${battle.id} belongs to season ${battle.seasonId}, " +
                        "not ${loaded.season.id}",
                )
            }
            val war = warsById[battle.warId]
                ?: throw RuntimeIntegrityException(
                    "Battle ${battle.id} references missing war ${battle.warId}",
                )
            if (setOf(
                    battle.attackingCivilizationId,
                    battle.defendingCivilizationId,
                ) != war.civilizationIds
            ) {
                throw RuntimeIntegrityException(
                    "Battle ${battle.id} parties do not match war ${war.id}",
                )
            }
            val participants = loaded.battleParticipants[battle.id].orEmpty()
            if (participants.map(BattleParticipant::playerId).toSet().size != participants.size) {
                throw RuntimeIntegrityException("Battle ${battle.id} has duplicate participants")
            }
            for (participant in participants) {
                val expectedCivilizationId = when (participant.side) {
                    BattleSide.ATTACKER -> battle.attackingCivilizationId
                    BattleSide.DEFENDER -> battle.defendingCivilizationId
                }
                if (participant.seasonId != battle.seasonId ||
                    participant.civilizationId != expectedCivilizationId
                ) {
                    throw RuntimeIntegrityException(
                        "Participant ${participant.playerId} does not match battle ${battle.id}",
                    )
                }
            }
            if (battle.status == BattleStatus.ACTIVE ||
                battle.status == BattleStatus.RESOLVING
            ) {
                listOf(
                    battle.attackingCivilizationId,
                    battle.defendingCivilizationId,
                ).forEach { civilizationId ->
                    openBattleParticipations.put(civilizationId, battle)?.let { existing ->
                        throw RuntimeIntegrityException(
                            "Civilization $civilizationId participates in open battles " +
                                "${existing.id} and ${battle.id}",
                        )
                    }
                }
                if (war.status != WarStatus.ACTIVE) {
                    throw RuntimeIntegrityException(
                        "Open battle ${battle.id} belongs to ${war.status} war ${war.id}",
                    )
                }
                val triggerClaim = claimsById[battle.triggerClaimId]
                    ?: throw RuntimeIntegrityException(
                        "Open battle ${battle.id} references missing trigger claim " +
                            battle.triggerClaimId,
                    )
                if (triggerClaim.civilizationId != battle.defendingCivilizationId) {
                    throw RuntimeIntegrityException(
                        "Battle ${battle.id} trigger claim ${triggerClaim.id} is not defender land",
                    )
                }
                if (participants.none { it.side == BattleSide.ATTACKER } ||
                    participants.none { it.side == BattleSide.DEFENDER }
                ) {
                    throw RuntimeIntegrityException(
                        "Open battle ${battle.id} requires participants on both sides",
                    )
                }
            }
        }
    }

    private fun buildActiveBattleEligibility(
        loaded: LoadedActiveSeason.Present,
    ): List<ActiveBattleEligibilityRuntimeState> {
        if (loaded.season.status != SeasonStatus.WAR) {
            return emptyList()
        }
        val warsById = loaded.wars.associateBy(War::id)
        val claimsByCivilization = loaded.claims.groupBy(Claim::civilizationId)
        return loaded.battles
            .filter { it.status == BattleStatus.ACTIVE }
            .map { battle ->
                val participants = loaded.battleParticipants.getValue(battle.id)
                ActiveBattleEligibilityRuntimeState(
                    war = warsById.getValue(battle.warId),
                    battle = battle,
                    participants = participants,
                    opposingClaimIdsByCivilization = mapOf(
                        battle.attackingCivilizationId to
                            claimsByCivilization[battle.defendingCivilizationId]
                                .orEmpty()
                                .mapTo(linkedSetOf(), Claim::id),
                        battle.defendingCivilizationId to
                            claimsByCivilization[battle.attackingCivilizationId]
                                .orEmpty()
                                .mapTo(linkedSetOf(), Claim::id),
                    ),
                    opposingPlayerIdsByCivilization = mapOf(
                        battle.attackingCivilizationId to participants
                            .filter { it.side == BattleSide.DEFENDER }
                            .mapTo(linkedSetOf(), BattleParticipant::playerId),
                        battle.defendingCivilizationId to participants
                            .filter { it.side == BattleSide.ATTACKER }
                            .mapTo(linkedSetOf(), BattleParticipant::playerId),
                    ),
                )
            }
    }

    private fun publishFatal(
        failure: Throwable,
        afterPublishing: () -> Unit,
    ) {
        dispatchToServer {
            state = CivilizationsRuntimeState.Failed(failure)
            afterPublishing()
            fatalFailureHandler(failure)
        }
    }

    private fun dispatchToServer(action: () -> Unit) {
        if (closed.get()) {
            return
        }
        serverThread.execute {
            if (!closed.get()) {
                action()
            }
        }
    }

    companion object {
        fun sqlite(
            databasePath: Path,
            claimRules: ClaimRules,
            phaseRules: GameplayPhaseRules = GameplayPhaseRules(),
            serverThread: Executor,
            fatalFailureHandler: (Throwable) -> Unit = {},
            idGenerator: CivilizationsIdGenerator = UuidCivilizationsIdGenerator(),
            clock: Clock = Clock.systemUTC(),
            worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, STORAGE_THREAD_NAME).apply { isDaemon = false }
            },
        ): CivilizationsRuntime {
            val connectionFactory = SqliteConnectionFactory(databasePath)
            return CivilizationsRuntime(
                repository = JdbcCivilizationsRepository(connectionFactory),
                migrator = SchemaMigrator(connectionFactory, clock = clock),
                claimRules = claimRules,
                phaseRules = phaseRules,
                idGenerator = idGenerator,
                clock = clock,
                serverThread = serverThread,
                worker = worker,
                fatalFailureHandler = fatalFailureHandler,
            )
        }

        private const val STORAGE_THREAD_NAME = "civilizations-v2-storage"
        private const val SHUTDOWN_TIMEOUT_SECONDS = 5L
    }
}

class RuntimeMutationScope internal constructor(
    val repository: CivilizationsRepository,
    val seasons: SeasonService,
    val civilizations: CivilizationService,
    val claims: ClaimService,
    val wars: WarService,
    val damageJournal: DamageJournalService,
) {
    fun activeSeasonId(): SeasonId? = repository.read { findActiveSeasonId() }

    fun findActiveCivilization(reference: String): Civilization? {
        val seasonId = activeSeasonId() ?: return null
        val id = runCatching { CivilizationId(java.util.UUID.fromString(reference)) }.getOrNull()
        if (id != null) {
            return repository.read {
                findCivilization(id)?.takeIf { it.seasonId == seasonId }
            }
        }
        val name = try {
            CivilizationName.from(reference)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return repository.read { findCivilizationByName(seasonId, name) }
    }

    fun findSeason(reference: String): Season? {
        val id = runCatching { SeasonId(java.util.UUID.fromString(reference)) }.getOrNull()
        return repository.read {
            if (id != null) {
                findSeason(id)
            } else {
                listSeasons().singleOrNull { it.name.equals(reference, ignoreCase = true) }
            }
        }
    }
}

sealed interface CivilizationsRuntimeState {
    data object Stopped : CivilizationsRuntimeState

    data object Starting : CivilizationsRuntimeState

    data class Ready(
        val activeSeason: ActiveSeasonRuntimeState?,
    ) : CivilizationsRuntimeState

    data class Failed(
        val failure: Throwable,
    ) : CivilizationsRuntimeState
}

data class ActiveSeasonRuntimeState(
    val season: Season,
    val civilizations: List<Civilization>,
    val memberships: Map<CivilizationId, List<Membership>>,
    val claimIndex: ClaimSpatialIndex,
    val protection: ProtectionService,
    val wars: List<War>,
    val battles: List<Battle>,
    val battleParticipants: Map<BattleId, List<BattleParticipant>>,
    val activeBattleEligibility: List<ActiveBattleEligibilityRuntimeState>,
)

/**
 * Published combat eligibility only. Paper protection deliberately does not
 * consume it until the next slice can journal block mutations before allowing them.
 */
data class ActiveBattleEligibilityRuntimeState(
    val war: War,
    val battle: Battle,
    val participants: List<BattleParticipant>,
    val opposingClaimIdsByCivilization: Map<CivilizationId, Set<ClaimId>>,
    val opposingPlayerIdsByCivilization: Map<CivilizationId, Set<PlayerId>>,
)

sealed interface RuntimeStartOutcome {
    data class Ready(
        val migration: SchemaMigrationReport,
        val state: CivilizationsRuntimeState.Ready,
    ) : RuntimeStartOutcome

    data class Failed(val failure: Throwable) : RuntimeStartOutcome
}

sealed interface RuntimeMutationOutcome<out T> {
    data class Completed<T>(
        val result: ApplicationResult<T>,
        val state: CivilizationsRuntimeState.Ready,
    ) : RuntimeMutationOutcome<T>

    data class NotReady(
        val state: CivilizationsRuntimeState,
    ) : RuntimeMutationOutcome<Nothing>

    data class Failed(val failure: Throwable) : RuntimeMutationOutcome<Nothing>
}

class RuntimeIntegrityException(message: String) : IllegalStateException(message)

private sealed interface LoadedActiveSeason {
    data object None : LoadedActiveSeason

    data class Present(
        val season: Season,
        val civilizations: List<Civilization>,
        val memberships: Map<CivilizationId, List<Membership>>,
        val claims: List<Claim>,
        val wars: List<War>,
        val battles: List<Battle>,
        val battleParticipants: Map<BattleId, List<BattleParticipant>>,
    ) : LoadedActiveSeason
}
