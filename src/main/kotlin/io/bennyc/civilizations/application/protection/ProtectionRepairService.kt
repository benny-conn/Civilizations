package io.bennyc.civilizations.application.protection

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.economy.EconomyLedger
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.LedgerTransactionRequest
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.protection.ExposureDamageSiteId
import io.bennyc.civilizations.domain.protection.ProtectionRepairItemStatus
import io.bennyc.civilizations.domain.protection.ProtectionRepairJob
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobId
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobItem
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobStatus
import io.bennyc.civilizations.domain.protection.ReportedExposureDamage
import io.bennyc.civilizations.domain.war.BattleStatus
import java.time.Clock
import kotlin.math.ceil

class ProtectionRepairService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
    private val economyRules: EconomyRules,
) {
    private val ledger = EconomyLedger(idGenerator, clock)

    fun listDamage(
        civilizationId: CivilizationId,
        afterSiteId: ExposureDamageSiteId?,
        limit: Int,
    ): List<ReportedExposureDamage> = repository.read {
        listUnresolvedExposureDamage(civilizationId, afterSiteId, limit)
    }

    fun assess(
        civilizationId: CivilizationId,
        observations: List<ProtectionDamageObservation>,
    ): ApplicationResult<ProtectionRepairAssessment> = repository.transaction {
        val damage = allDamage(civilizationId)
        when (val classified = classify(
            damage,
            observations,
            countResolvedExposureDamageInOpenExposures(civilizationId),
        )) {
            is ApplicationResult.Applied -> {
                classified.value.restored.forEach {
                    resolveExposureDamageSite(it.site.id, clock.instant())
                }
                classified
            }
            is ApplicationResult.Unchanged -> classified
            is ApplicationResult.Rejected -> classified
        }
    }

    fun start(request: StartProtectionRepair): ApplicationResult<ProtectionRepairJob> =
        repository.transaction {
            findProtectionRepairJobByIdempotencyKey(request.idempotencyKey)?.let { existing ->
                return@transaction if (
                    existing.civilizationId == request.civilizationId &&
                    existing.initiatedByPlayerId == request.initiatedByPlayerId &&
                    existing.targetCompletionBasisPoints == request.targetCompletionBasisPoints
                ) {
                    ApplicationResult.Unchanged(existing)
                } else {
                    ApplicationResult.Rejected(
                        ProtectionRepairIdempotencyConflict(request.idempotencyKey),
                    )
                }
            }
            findOpenProtectionRepairJob(request.civilizationId)?.let { existing ->
                return@transaction ApplicationResult.Rejected(
                    ProtectionRepairAlreadyOpen(existing.id),
                )
            }
            val civilization = findCivilization(request.civilizationId)
                ?: return@transaction ApplicationResult.Rejected(
                    ProtectionRepairCivilizationUnavailable(request.civilizationId),
                )
            if (listOpenBattlesForCivilization(civilization.id).any {
                    it.status == BattleStatus.ACTIVE || it.status == BattleStatus.RESOLVING
                }
            ) {
                return@transaction ApplicationResult.Rejected(
                    ProtectionRepairSuspendedForBattle(civilization.id),
                )
            }
            val actor = request.initiatedByPlayerId
                ?: return@transaction ApplicationResult.Rejected(
                    ProtectionRepairAuthorityRequired,
                )
            val membership = findMembership(civilization.seasonId, actor)
            if (membership?.civilizationId != civilization.id ||
                membership.role !in economyRules.repair.ordinaryInitiatorRoles
            ) {
                return@transaction ApplicationResult.Rejected(
                    ProtectionRepairAuthorityRequired,
                )
            }
            val damage = allDamage(request.civilizationId)
            val classified = when (val result = classify(
                damage,
                request.observations,
                countResolvedExposureDamageInOpenExposures(civilization.id),
            )) {
                is ApplicationResult.Applied -> result.value
                is ApplicationResult.Unchanged -> result.value
                is ApplicationResult.Rejected -> return@transaction result
            }
            classified.restored.forEach {
                resolveExposureDamageSite(it.site.id, clock.instant())
            }
            val targetCount = ceil(
                classified.totalDamageCount.toDouble() *
                    request.targetCompletionBasisPoints.toDouble() / 10_000.0,
            ).toLong()
            if (targetCount <= classified.restoredCount) {
                return@transaction ApplicationResult.Rejected(
                    ProtectionRepairTargetReached(classified.completionBasisPoints),
                )
            }
            val needed = targetCount - classified.restoredCount
            if (needed > classified.repairable.size) {
                return@transaction ApplicationResult.Rejected(
                    ProtectionRepairTargetUnavailable(
                        request.targetCompletionBasisPoints,
                        classified.repairable.size.toLong(),
                        classified.conflictCount,
                    ),
                )
            }
            val selected = classified.repairable
                .sortedWith(compareBy({ it.site.position.worldId.value },
                    { it.site.position.y }, { it.site.position.x }, { it.site.position.z },
                    { it.site.id.toString() }))
                .take(needed.toInt())
            val itemPrices = selected.map { damageSite ->
                if (damageSite.site.originalState.isAirLike) {
                    economyRules.repair.removePlacementUnitPrice
                } else {
                    economyRules.repair.restoreOriginalUnitPrice
                }
            }
            val gross = try {
                itemPrices.fold(MoneyAmount.ZERO, MoneyAmount::plus)
            } catch (_: RuntimeException) {
                return@transaction ApplicationResult.Rejected(ProtectionRepairPriceOverflow)
            }
            val paymentId = if (gross == MoneyAmount.ZERO) {
                null
            } else {
                when (val payment = ledger.post(
                    this,
                    LedgerTransactionRequest(
                        seasonId = civilization.seasonId,
                        idempotencyKey = "protection-repair:${request.idempotencyKey}",
                        kind = LedgerTransactionKind.LAND_PROTECTION_REPAIR,
                        postings = listOf(LedgerPosting(civilization.id, gross.negate())),
                        referenceType = "LAND_PROTECTION_REPAIR",
                        referenceId = request.idempotencyKey,
                        actorPlayerId = request.initiatedByPlayerId,
                        description = "Restore journaled land-exposure damage",
                    ),
                )) {
                    is ApplicationResult.Applied -> payment.value.id
                    is ApplicationResult.Unchanged -> payment.value.id
                    is ApplicationResult.Rejected -> return@transaction payment
                }
            }
            val now = clock.instant()
            val job = ProtectionRepairJob(
                id = idGenerator.newProtectionRepairJobId(),
                seasonId = civilization.seasonId,
                civilizationId = civilization.id,
                initiatedByPlayerId = request.initiatedByPlayerId,
                idempotencyKey = request.idempotencyKey,
                targetCompletionBasisPoints = request.targetCompletionBasisPoints,
                totalDamageCount = classified.totalDamageCount,
                observedRestoredCount = classified.restoredCount,
                observedRepairableCount = classified.repairable.size.toLong(),
                observedConflictCount = classified.conflictCount,
                selectedCount = selected.size.toLong(),
                restoreOriginalUnitPrice = economyRules.repair.restoreOriginalUnitPrice,
                removePlacementUnitPrice = economyRules.repair.removePlacementUnitPrice,
                grossCost = gross,
                paymentLedgerTransactionId = paymentId,
                status = ProtectionRepairJobStatus.PENDING,
                nextItemOrdinal = 0,
                restoredCount = 0,
                skippedConflictCount = 0,
                failedCount = 0,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
                failureMessage = null,
            )
            insertProtectionRepairJob(job)
            selected.forEachIndexed { index, reported ->
                insertProtectionRepairJobItem(
                    ProtectionRepairJobItem(
                        jobId = job.id,
                        siteId = reported.site.id,
                        ordinal = index.toLong(),
                        position = reported.site.position,
                        expectedState = reported.latestEvent.expectedState,
                        restoreState = reported.site.originalState,
                        unitPrice = if (reported.site.originalState.isAirLike) {
                            economyRules.repair.removePlacementUnitPrice
                        } else {
                            economyRules.repair.restoreOriginalUnitPrice
                        },
                        status = ProtectionRepairItemStatus.PENDING,
                        processedAt = null,
                        failureMessage = null,
                    ),
                )
            }
            ApplicationResult.Applied(job)
        }

    fun begin(jobId: ProtectionRepairJobId): ApplicationResult<ProtectionRepairJob> =
        transition(jobId, setOf(ProtectionRepairJobStatus.PENDING, ProtectionRepairJobStatus.PAUSED)) {
            copy(status = ProtectionRepairJobStatus.RUNNING, updatedAt = clock.instant())
        }

    fun pause(jobId: ProtectionRepairJobId): ApplicationResult<ProtectionRepairJob> =
        transition(jobId, setOf(ProtectionRepairJobStatus.RUNNING)) {
            copy(status = ProtectionRepairJobStatus.PAUSED, updatedAt = clock.instant())
        }

    fun cancel(jobId: ProtectionRepairJobId): ApplicationResult<ProtectionRepairJob> =
        transition(
            jobId,
            setOf(
                ProtectionRepairJobStatus.PENDING,
                ProtectionRepairJobStatus.RUNNING,
                ProtectionRepairJobStatus.PAUSED,
            ),
        ) {
            val now = clock.instant()
            copy(status = ProtectionRepairJobStatus.CANCELLED, updatedAt = now, completedAt = now)
        }

    fun recordItem(
        jobId: ProtectionRepairJobId,
        ordinal: Long,
        result: ProtectionRepairItemStatus,
        failureMessage: String? = null,
    ): ApplicationResult<ProtectionRepairJob> = repository.transaction {
        val job = findProtectionRepairJob(jobId)
            ?: return@transaction ApplicationResult.Rejected(ProtectionRepairJobNotFound(jobId))
        if (job.status != ProtectionRepairJobStatus.RUNNING || ordinal != job.nextItemOrdinal) {
            return@transaction ApplicationResult.Rejected(ProtectionRepairCursorConflict)
        }
        val item = listProtectionRepairJobItems(jobId, ordinal - 1, 1).singleOrNull()
            ?: return@transaction ApplicationResult.Rejected(ProtectionRepairCursorConflict)
        if (item.ordinal != ordinal || item.status != ProtectionRepairItemStatus.PENDING) {
            return@transaction ApplicationResult.Rejected(ProtectionRepairCursorConflict)
        }
        val now = clock.instant()
        updateProtectionRepairJobItem(
            item.copy(status = result, processedAt = now, failureMessage = failureMessage),
        )
        if (result == ProtectionRepairItemStatus.RESTORED) {
            resolveExposureDamageSite(item.siteId, now)
        }
        val next = ordinal + 1
        val completed = next >= job.selectedCount
        val updated = job.copy(
            status = if (completed) ProtectionRepairJobStatus.COMPLETED else job.status,
            nextItemOrdinal = next,
            restoredCount = job.restoredCount + if (result == ProtectionRepairItemStatus.RESTORED) 1 else 0,
            skippedConflictCount = job.skippedConflictCount +
                if (result == ProtectionRepairItemStatus.SKIPPED_CONFLICT) 1 else 0,
            failedCount = job.failedCount + if (result == ProtectionRepairItemStatus.FAILED) 1 else 0,
            updatedAt = now,
            completedAt = now.takeIf { completed },
        )
        updateProtectionRepairJob(updated)
        ApplicationResult.Applied(updated)
    }

    fun recoverInterruptedJobs(): Int = repository.transaction {
        val jobs = listProtectionRepairJobsByStatus(setOf(ProtectionRepairJobStatus.RUNNING), 1_000)
        jobs.forEach { updateProtectionRepairJob(it.copy(status = ProtectionRepairJobStatus.PAUSED, updatedAt = clock.instant())) }
        jobs.size
    }

    fun findJob(id: ProtectionRepairJobId): ProtectionRepairJob? =
        repository.read { findProtectionRepairJob(id) }

    fun listItems(id: ProtectionRepairJobId, after: Long?, limit: Int) =
        repository.read { listProtectionRepairJobItems(id, after, limit) }

    fun listJobs(
        statuses: Set<ProtectionRepairJobStatus>,
        limit: Int,
    ): ApplicationResult<List<ProtectionRepairJob>> = repository.read {
        ApplicationResult.Applied(listProtectionRepairJobsByStatus(statuses, limit))
    }

    private fun transition(
        jobId: ProtectionRepairJobId,
        allowed: Set<ProtectionRepairJobStatus>,
        update: ProtectionRepairJob.() -> ProtectionRepairJob,
    ): ApplicationResult<ProtectionRepairJob> = repository.transaction {
        val job = findProtectionRepairJob(jobId)
            ?: return@transaction ApplicationResult.Rejected(ProtectionRepairJobNotFound(jobId))
        if (job.status !in allowed) {
            return@transaction ApplicationResult.Rejected(
                ProtectionRepairInvalidTransition(job.status),
            )
        }
        val updated = job.update()
        updateProtectionRepairJob(updated)
        ApplicationResult.Applied(updated)
    }

    private fun io.bennyc.civilizations.application.persistence.CivilizationsReadContext.allDamage(
        civilizationId: CivilizationId,
    ): List<ReportedExposureDamage> {
        val result = mutableListOf<ReportedExposureDamage>()
        var cursor: ExposureDamageSiteId? = null
        do {
            val page = listUnresolvedExposureDamage(civilizationId, cursor, PAGE_SIZE)
            result += page
            cursor = page.lastOrNull()?.site?.id
        } while (page.size == PAGE_SIZE)
        return result
    }

    private fun classify(
        damage: List<ReportedExposureDamage>,
        observations: List<ProtectionDamageObservation>,
        previouslyResolvedCount: Long,
    ): ApplicationResult<ProtectionRepairAssessment> {
        require(previouslyResolvedCount >= 0)
        val byId = observations.associateBy(ProtectionDamageObservation::siteId)
        if (byId.size != observations.size || byId.keys != damage.mapTo(linkedSetOf()) { it.site.id }) {
            return ApplicationResult.Rejected(ProtectionRepairObservationMismatch)
        }
        val restored = mutableListOf<ReportedExposureDamage>()
        val repairable = mutableListOf<ReportedExposureDamage>()
        var conflicts = 0L
        damage.forEach { reported ->
            val current = byId.getValue(reported.site.id).currentState
            when (current) {
                reported.site.originalState -> restored += reported
                reported.latestEvent.expectedState -> repairable += reported
                else -> conflicts++
            }
        }
        val totalDamageCount = Math.addExact(previouslyResolvedCount, damage.size.toLong())
        val restoredCount = Math.addExact(previouslyResolvedCount, restored.size.toLong())
        val completion = if (totalDamageCount == 0L) 10_000 else
            ((restoredCount * 10_000L) / totalDamageCount).toInt()
        val repairableCost = try {
            repairable.fold(MoneyAmount.ZERO) { total, reported ->
                total.plus(
                    if (reported.site.originalState.isAirLike) {
                        economyRules.repair.removePlacementUnitPrice
                    } else {
                        economyRules.repair.restoreOriginalUnitPrice
                    },
                )
            }
        } catch (_: RuntimeException) {
            return ApplicationResult.Rejected(ProtectionRepairPriceOverflow)
        }
        return ApplicationResult.Applied(
            ProtectionRepairAssessment(
                totalDamageCount = totalDamageCount,
                restoredCount = restoredCount,
                repairable = repairable,
                conflictCount = conflicts,
                completionBasisPoints = completion,
                repairableCost = repairableCost,
                restored = restored,
            ),
        )
    }

    companion object {
        private const val PAGE_SIZE = 1_000
    }
}

data class ProtectionDamageObservation(
    val siteId: ExposureDamageSiteId,
    val currentState: SimpleBlockSnapshot,
)

data class ProtectionRepairAssessment(
    val totalDamageCount: Long,
    val restoredCount: Long,
    val repairable: List<ReportedExposureDamage>,
    val conflictCount: Long,
    val completionBasisPoints: Int,
    val repairableCost: MoneyAmount,
    internal val restored: List<ReportedExposureDamage>,
)

data class StartProtectionRepair(
    val civilizationId: CivilizationId,
    val initiatedByPlayerId: PlayerId?,
    val targetCompletionBasisPoints: Int,
    val observations: List<ProtectionDamageObservation>,
    val idempotencyKey: String,
) {
    init {
        require(targetCompletionBasisPoints in 1..10_000)
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 160)
    }
}

data class ProtectionRepairAlreadyOpen(val jobId: ProtectionRepairJobId) : ApplicationFailure {
    override val description = "Protection repair $jobId is already open"
}

data class ProtectionRepairIdempotencyConflict(val key: String) : ApplicationFailure {
    override val description = "Protection repair idempotency key '$key' was already used differently"
}

data class ProtectionRepairCivilizationUnavailable(val civilizationId: CivilizationId) :
    ApplicationFailure {
    override val description = "Civilization $civilizationId is unavailable for protection repair"
}

data object ProtectionRepairAuthorityRequired : ApplicationFailure {
    override val description = "Only an authorized civilization role may start this repair"
}

data class ProtectionRepairSuspendedForBattle(val civilizationId: CivilizationId) :
    ApplicationFailure {
    override val description = "Protection restoration for $civilizationId waits until its battle closes"
}

data class ProtectionRepairTargetReached(val completionBasisPoints: Int) : ApplicationFailure {
    override val description = "Land exposure damage is already at this repair target"
}

data class ProtectionRepairTargetUnavailable(
    val targetBasisPoints: Int,
    val repairableCount: Long,
    val conflictCount: Long,
) : ApplicationFailure {
    override val description = "That target is unreachable with $repairableCount repairable and $conflictCount conflicted blocks"
}

data object ProtectionRepairObservationMismatch : ApplicationFailure {
    override val description = "Current-world observations do not exactly cover unresolved exposure damage"
}

data object ProtectionRepairPriceOverflow : ApplicationFailure {
    override val description = "Protection repair price exceeds the supported money range"
}

data class ProtectionRepairJobNotFound(val jobId: ProtectionRepairJobId) : ApplicationFailure {
    override val description = "Protection repair $jobId does not exist"
}

data object ProtectionRepairCursorConflict : ApplicationFailure {
    override val description = "Protection repair cursor changed; retry from the durable cursor"
}

data class ProtectionRepairInvalidTransition(val status: ProtectionRepairJobStatus) :
    ApplicationFailure {
    override val description = "Protection repair cannot transition while $status"
}
