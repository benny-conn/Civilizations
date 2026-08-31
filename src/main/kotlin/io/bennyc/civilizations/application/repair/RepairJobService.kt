package io.bennyc.civilizations.application.repair

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.economy.EconomyAmountOverflow
import io.bennyc.civilizations.application.economy.EconomyLedger
import io.bennyc.civilizations.application.economy.EconomyNotInitialized
import io.bennyc.civilizations.application.economy.EconomyRules
import io.bennyc.civilizations.application.economy.LedgerTransactionRequest
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsReadContext
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.damage.BlockChangeCursor
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.DamageCostCategory
import io.bennyc.civilizations.domain.damage.DamageReportEligibility
import io.bennyc.civilizations.domain.damage.ReportedBattleBlockChange
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransaction
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.repair.RepairFundingMode
import io.bennyc.civilizations.domain.repair.RepairJob
import io.bennyc.civilizations.domain.repair.RepairJobId
import io.bennyc.civilizations.domain.repair.RepairJobItem
import io.bennyc.civilizations.domain.repair.RepairJobItemStatus
import io.bennyc.civilizations.domain.repair.RepairJobStatus
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleStatus
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock

/**
 * Owns durable repair plans and their economic transaction. Live world reads and writes are
 * adapter concerns: an adapter supplies observations, then later executes persisted work items.
 */
class RepairJobService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
    private val rules: EconomyRules,
) {
    private val ledger = EconomyLedger(idGenerator, clock)

    fun loadAssessmentBasis(
        battleId: BattleId,
        civilizationId: CivilizationId,
    ): ApplicationResult<RepairAssessmentBasis> = repository.read {
        loadAssessmentBasis(battleId, civilizationId)
    }

    /** Pure assessment suitable for the server-thread observation boundary. */
    fun assess(
        basis: RepairAssessmentBasis,
        observations: List<CurrentRepairObservation>,
    ): ApplicationResult<RepairAssessment> {
        val statesById = observations.associateBy(CurrentRepairObservation::blockChangeId)
        if (statesById.size != observations.size ||
            statesById.keys != basis.eligibleChanges.mapTo(linkedSetOf()) {
                it.journalEntry.id
            }
        ) {
            return ApplicationResult.Rejected(RepairObservationCoverageMismatch)
        }

        val entries = basis.eligibleChanges.map { change ->
            val current = checkNotNull(statesById[change.journalEntry.id]).currentState
            val condition = when (current) {
                change.journalEntry.originalState -> RepairCoordinateCondition.RESTORED
                change.reportEntry.finalState -> RepairCoordinateCondition.REPAIRABLE
                else -> RepairCoordinateCondition.CONFLICT
            }
            AssessedRepairChange(change, condition)
        }
        return ApplicationResult.Applied(
            RepairAssessment(
                basis = basis,
                entries = entries,
                restoredCount = entries.countCondition(RepairCoordinateCondition.RESTORED),
                repairableCount = entries.countCondition(RepairCoordinateCondition.REPAIRABLE),
                conflictCount = entries.countCondition(RepairCoordinateCondition.CONFLICT),
            ),
        )
    }

    fun quote(request: QuoteRepairRequest): ApplicationResult<RepairQuote> = repository.read {
        when (val basis = loadAssessmentBasis(request.battleId, request.civilizationId)) {
            is ApplicationResult.Rejected -> basis
            is ApplicationResult.Applied -> quote(
                basis.value,
                request.targetCompletionBasisPoints,
                request.observations,
                request.fundingMode,
            )
            is ApplicationResult.Unchanged -> error("Assessment basis cannot be unchanged")
        }
    }

    /** The payment, repair job, and every selected item are committed atomically. */
    fun create(
        request: CreateRepairJobRequest,
        maximumGrossCost: MoneyAmount? = null,
    ): ApplicationResult<CreatedRepairJob> =
        repository.transaction {
            if (request.idempotencyKey.isBlank() ||
                request.idempotencyKey.length > RepairJob.MAX_KEY_LENGTH
            ) {
                return@transaction ApplicationResult.Rejected(
                    InvalidRepairIdempotencyKey(request.idempotencyKey),
                )
            }
            findRepairJobByIdempotencyKey(request.idempotencyKey)?.let { existing ->
                return@transaction if (existing.matches(request)) {
                    if (maximumGrossCost != null &&
                        existing.grossCost.minorUnits > maximumGrossCost.minorUnits
                    ) {
                        ApplicationResult.Rejected(
                            RepairConfirmationPriceExceeded(
                                confirmedMaximum = maximumGrossCost,
                                currentPrice = existing.grossCost,
                            ),
                        )
                    } else {
                        ApplicationResult.Unchanged(
                            CreatedRepairJob(existing, findPayment(existing), null),
                        )
                    }
                } else {
                    ApplicationResult.Rejected(
                        RepairIdempotencyConflict(request.idempotencyKey),
                    )
                }
            }

            val basis = when (val result = loadAssessmentBasis(
                request.battleId,
                request.civilizationId,
            )) {
                is ApplicationResult.Applied -> result.value
                is ApplicationResult.Rejected -> return@transaction result
                is ApplicationResult.Unchanged -> error("Assessment basis cannot be unchanged")
            }
            if (findActiveSeasonId() != basis.battle.seasonId) {
                return@transaction ApplicationResult.Rejected(
                    RepairSeasonNotActive(basis.battle.seasonId),
                )
            }
            if (findOpenRepairJob(request.battleId, request.civilizationId) != null) {
                return@transaction ApplicationResult.Rejected(
                    OpenRepairJobAlreadyExists(request.battleId, request.civilizationId),
                )
            }
            if (request.fundingMode == RepairFundingMode.ORDINARY) {
                val initiator = request.initiatedByPlayerId
                    ?: return@transaction ApplicationResult.Rejected(
                        OrdinaryRepairRequiresPlayer(request.civilizationId),
                    )
                val membership = findMembership(basis.battle.seasonId, initiator)
                if (membership?.civilizationId != request.civilizationId) {
                    return@transaction ApplicationResult.Rejected(
                        RepairInitiatorMustBeMember(
                            initiator,
                            request.civilizationId,
                        ),
                    )
                }
                if (membership.role !in rules.repair.ordinaryInitiatorRoles) {
                    return@transaction ApplicationResult.Rejected(
                        RepairInitiatorRoleNotAllowed(
                            initiator,
                            request.civilizationId,
                        ),
                    )
                }
            }

            val quote = when (val result = quote(
                basis,
                request.targetCompletionBasisPoints,
                request.observations,
                request.fundingMode,
            )) {
                is ApplicationResult.Applied -> result.value
                is ApplicationResult.Rejected -> return@transaction result
                is ApplicationResult.Unchanged -> error("Repair quote cannot be unchanged")
            }
            if (maximumGrossCost != null &&
                quote.grossCost.minorUnits > maximumGrossCost.minorUnits
            ) {
                return@transaction ApplicationResult.Rejected(
                    RepairConfirmationPriceExceeded(
                        confirmedMaximum = maximumGrossCost,
                        currentPrice = quote.grossCost,
                    ),
                )
            }
            val jobId = idGenerator.newRepairJobId()
            val payment = if (request.fundingMode == RepairFundingMode.ORDINARY) {
                val postings = buildList {
                    add(LedgerPosting(request.civilizationId, quote.grossCost.negate()))
                    if (quote.victorCivilizationId != null &&
                        quote.victorProceeds != MoneyAmount.ZERO
                    ) {
                        add(LedgerPosting(quote.victorCivilizationId, quote.victorProceeds))
                    }
                }
                when (val result = ledger.post(
                    this,
                    LedgerTransactionRequest(
                        seasonId = basis.battle.seasonId,
                        idempotencyKey = "repair:$jobId:payment",
                        kind = LedgerTransactionKind.REPAIR_PAYMENT,
                        postings = postings,
                        referenceType = "REPAIR_JOB",
                        referenceId = jobId.toString(),
                        actorPlayerId = request.initiatedByPlayerId,
                        description = "Battle damage repair payment",
                    ),
                )) {
                    is ApplicationResult.Applied -> result.value
                    is ApplicationResult.Unchanged -> result.value
                    is ApplicationResult.Rejected -> return@transaction result
                }
            } else {
                null
            }

            val now = clock.instant()
            val selectedRestore = quote.selectedChanges.count {
                it.reportEntry.costCategory == DamageCostCategory.RESTORE_ORIGINAL_BLOCK
            }.toLong()
            val selectedRemove = quote.selectedChanges.size.toLong() - selectedRestore
            val job = RepairJob(
                id = jobId,
                seasonId = basis.battle.seasonId,
                battleId = request.battleId,
                civilizationId = request.civilizationId,
                initiatedByPlayerId = request.initiatedByPlayerId,
                fundingMode = request.fundingMode,
                idempotencyKey = request.idempotencyKey,
                targetCompletionBasisPoints = request.targetCompletionBasisPoints,
                totalEligibleCount = quote.assessment.totalEligibleCount,
                observedRestoredCount = quote.assessment.restoredCount,
                observedRepairableCount = quote.assessment.repairableCount,
                observedConflictCount = quote.assessment.conflictCount,
                selectedRestoreOriginalCount = selectedRestore,
                selectedRemovePlacementCount = selectedRemove,
                restoreOriginalUnitPrice = quote.restoreOriginalUnitPrice,
                removePlacementUnitPrice = quote.removePlacementUnitPrice,
                grossCost = quote.grossCost,
                victorShareBasisPoints = quote.victorShareBasisPoints,
                victorCivilizationId = quote.victorCivilizationId,
                victorProceeds = quote.victorProceeds,
                paymentLedgerTransactionId = payment?.id,
                status = RepairJobStatus.QUEUED,
                nextItemOrdinal = 0,
                restoredCount = 0,
                skippedConflictCount = 0,
                failedCount = 0,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
                failureMessage = null,
            )
            insertRepairJob(job)
            quote.selectedChanges.forEachIndexed { ordinal, change ->
                insertRepairJobItem(
                    RepairJobItem(
                        repairJobId = job.id,
                        battleId = job.battleId,
                        blockChangeId = change.journalEntry.id,
                        ordinal = ordinal.toLong(),
                        unitPrice = quote.unitPrice(change),
                        status = RepairJobItemStatus.PENDING,
                        processedAt = null,
                        failureMessage = null,
                    ),
                )
            }
            ApplicationResult.Applied(CreatedRepairJob(job, payment, quote))
        }

    fun find(jobId: RepairJobId): ApplicationResult<RepairJob> = repository.read {
        findRepairJob(jobId)?.let { ApplicationResult.Applied(it) }
            ?: ApplicationResult.Rejected(RepairJobNotFound(jobId))
    }

    fun listForBattle(battleId: BattleId, limit: Int = 50): List<RepairJob> = repository.read {
        listRepairJobsForBattle(battleId, limit.coerceIn(1, MAX_INSPECTION_PAGE_SIZE))
    }

    fun listForCivilization(
        civilizationId: CivilizationId,
        limit: Int = 50,
    ): List<RepairJob> = repository.read {
        listRepairJobsForCivilization(
            civilizationId,
            limit.coerceIn(1, MAX_INSPECTION_PAGE_SIZE),
        )
    }

    fun startExecution(jobId: RepairJobId): ApplicationResult<RepairJob> = transition(jobId) {
        when (status) {
            RepairJobStatus.RUNNING -> ApplicationResult.Unchanged(this)
            RepairJobStatus.QUEUED,
            RepairJobStatus.PAUSED,
            -> updated(RepairJobStatus.RUNNING)
            RepairJobStatus.COMPLETED,
            RepairJobStatus.CANCELLED,
            RepairJobStatus.FAILED,
            -> ApplicationResult.Rejected(InvalidRepairJobTransition(id, status, "start"))
        }
    }

    fun pause(jobId: RepairJobId): ApplicationResult<RepairJob> = transition(jobId) {
        when (status) {
            RepairJobStatus.PAUSED -> ApplicationResult.Unchanged(this)
            RepairJobStatus.QUEUED,
            RepairJobStatus.RUNNING,
            -> updated(RepairJobStatus.PAUSED)
            RepairJobStatus.COMPLETED,
            RepairJobStatus.CANCELLED,
            RepairJobStatus.FAILED,
            -> ApplicationResult.Rejected(InvalidRepairJobTransition(id, status, "pause"))
        }
    }

    fun cancel(jobId: RepairJobId): ApplicationResult<RepairJob> = transition(jobId) {
        when (status) {
            RepairJobStatus.QUEUED,
            RepairJobStatus.RUNNING,
            RepairJobStatus.PAUSED,
            -> updated(RepairJobStatus.CANCELLED, terminal = true)
            RepairJobStatus.CANCELLED -> ApplicationResult.Unchanged(this)
            RepairJobStatus.COMPLETED,
            RepairJobStatus.FAILED,
            -> ApplicationResult.Rejected(InvalidRepairJobTransition(id, status, "cancel"))
        }
    }

    fun fail(jobId: RepairJobId, message: String): ApplicationResult<RepairJob> =
        transition(jobId) {
            when (status) {
                RepairJobStatus.RUNNING,
                RepairJobStatus.PAUSED,
                -> updated(
                    RepairJobStatus.FAILED,
                    terminal = true,
                    failure = message.take(RepairJob.MAX_FAILURE_LENGTH),
                )
                RepairJobStatus.FAILED -> ApplicationResult.Unchanged(this)
                RepairJobStatus.QUEUED,
                RepairJobStatus.COMPLETED,
                RepairJobStatus.CANCELLED,
                -> ApplicationResult.Rejected(InvalidRepairJobTransition(id, status, "fail"))
            }
        }

    /** A stopped server never assumes that live world mutations completed. */
    fun recoverInterruptedJobs(): Int {
        var recovered = 0
        while (true) {
            val count = repository.transaction {
                val running = listRepairJobsByStatus(
                    setOf(RepairJobStatus.RUNNING),
                    RECOVERY_BATCH_SIZE,
                )
                val now = clock.instant()
                running.forEach { updateRepairJob(it.copy(status = RepairJobStatus.PAUSED, updatedAt = now)) }
                running.size
            }
            recovered += count
            if (count < RECOVERY_BATCH_SIZE) return recovered
        }
    }

    fun loadWorkBatch(
        jobId: RepairJobId,
        limit: Int,
    ): ApplicationResult<RepairWorkBatch> = repository.read {
        if (limit !in 1..MAX_WORK_BATCH_SIZE) {
            return@read ApplicationResult.Rejected(InvalidRepairBatchSize(limit))
        }
        val job = findRepairJob(jobId)
            ?: return@read ApplicationResult.Rejected(RepairJobNotFound(jobId))
        if (job.status != RepairJobStatus.RUNNING) {
            return@read ApplicationResult.Rejected(
                InvalidRepairJobTransition(job.id, job.status, "load work"),
            )
        }
        val items = listRepairJobItems(
            repairJobId = job.id,
            afterOrdinal = job.nextItemOrdinal - 1,
            limit = limit,
        )
        val work = items.map { item ->
            check(item.status == RepairJobItemStatus.PENDING) {
                "Repair cursor ${job.nextItemOrdinal} points to processed item ${item.ordinal}"
            }
            val change = checkNotNull(findReportedBlockChange(job.battleId, item.blockChangeId)) {
                "Repair item ${item.blockChangeId} lost its immutable damage source"
            }
            RepairWorkItem(item, change)
        }
        ApplicationResult.Applied(RepairWorkBatch(job, work))
    }

    fun recordWorkBatch(request: RecordRepairWorkBatch): ApplicationResult<RepairJob> =
        repository.transaction {
            if (request.results.isEmpty() || request.results.size > MAX_WORK_BATCH_SIZE) {
                return@transaction ApplicationResult.Rejected(
                    InvalidRepairBatchSize(request.results.size),
                )
            }
            val job = findRepairJob(request.repairJobId)
                ?: return@transaction ApplicationResult.Rejected(
                    RepairJobNotFound(request.repairJobId),
                )
            if (job.status != RepairJobStatus.RUNNING) {
                return@transaction ApplicationResult.Rejected(
                    InvalidRepairJobTransition(job.id, job.status, "record work"),
                )
            }
            val expected = listRepairJobItems(
                job.id,
                job.nextItemOrdinal - 1,
                request.results.size,
            )
            if (expected.size != request.results.size ||
                expected.zip(request.results).any { (item, result) ->
                    item.ordinal != result.ordinal || item.blockChangeId != result.blockChangeId ||
                        item.status != RepairJobItemStatus.PENDING
                }
            ) {
                return@transaction ApplicationResult.Rejected(RepairBatchCursorConflict(job.id))
            }
            val now = clock.instant()
            request.results.forEachIndexed { index, result ->
                val item = expected[index]
                updateRepairJobItem(
                    item.copy(
                        status = result.status,
                        processedAt = now,
                        failureMessage = result.failureMessage?.take(RepairJob.MAX_FAILURE_LENGTH),
                    ),
                )
            }
            val restored = request.results.count { it.status == RepairJobItemStatus.RESTORED }
            val skipped = request.results.count {
                it.status == RepairJobItemStatus.SKIPPED_CONFLICT
            }
            val failed = request.results.count { it.status == RepairJobItemStatus.FAILED }
            val cursor = job.nextItemOrdinal + request.results.size
            val complete = cursor == job.selectedCount
            val updated = job.copy(
                status = if (complete) RepairJobStatus.COMPLETED else RepairJobStatus.RUNNING,
                nextItemOrdinal = cursor,
                restoredCount = job.restoredCount + restored,
                skippedConflictCount = job.skippedConflictCount + skipped,
                failedCount = job.failedCount + failed,
                updatedAt = now,
                completedAt = now.takeIf { complete },
            )
            updateRepairJob(updated)
            ApplicationResult.Applied(updated)
        }

    private fun CivilizationsReadContext.loadAssessmentBasis(
        battleId: BattleId,
        civilizationId: CivilizationId,
    ): ApplicationResult<RepairAssessmentBasis> {
        val battle = findBattle(battleId)
            ?: return ApplicationResult.Rejected(RepairBattleNotFound(battleId))
        if (battle.status != BattleStatus.CLOSED || findDamageReport(battleId) == null) {
            return ApplicationResult.Rejected(RepairBattleNotClosed(battleId))
        }
        if (civilizationId !in setOf(
                battle.attackingCivilizationId,
                battle.defendingCivilizationId,
            )
        ) {
            return ApplicationResult.Rejected(RepairCivilizationNotBattleParty(civilizationId))
        }
        val civilization = findCivilization(civilizationId)
            ?: return ApplicationResult.Rejected(RepairCivilizationNotBattleParty(civilizationId))
        if (civilization.status != CivilizationStatus.ACTIVE ||
            civilization.seasonId != battle.seasonId
        ) {
            return ApplicationResult.Rejected(RepairCivilizationUnavailable(civilizationId))
        }
        val ownedClaims = listClaims(civilizationId).mapTo(hashSetOf()) { it.id }
        val eligible = buildList {
            var cursor: BlockChangeCursor? = null
            do {
                val page = listReportedBlockChanges(battleId, cursor, REPORT_PAGE_SIZE)
                addAll(
                    page.filter {
                        it.reportEntry.eligibility == DamageReportEligibility.ELIGIBLE &&
                            it.journalEntry.claimId in ownedClaims
                    },
                )
                cursor = page.lastOrNull()?.cursor
            } while (page.size == REPORT_PAGE_SIZE)
        }
        if (eligible.isEmpty()) {
            return ApplicationResult.Rejected(NoEligibleRepairDamage(battleId, civilizationId))
        }
        return ApplicationResult.Applied(RepairAssessmentBasis(battle, civilizationId, eligible))
    }

    private fun CivilizationsReadContext.quote(
        basis: RepairAssessmentBasis,
        targetCompletionBasisPoints: Int,
        observations: List<CurrentRepairObservation>,
        fundingMode: RepairFundingMode,
    ): ApplicationResult<RepairQuote> {
        if (targetCompletionBasisPoints !in 1..RepairJob.MAX_BASIS_POINTS) {
            return ApplicationResult.Rejected(
                InvalidRepairTarget(targetCompletionBasisPoints),
            )
        }
        if (findSeasonEconomySettings(basis.battle.seasonId) == null) {
            return ApplicationResult.Rejected(EconomyNotInitialized(basis.battle.seasonId))
        }
        val assessment = when (val result = assess(basis, observations)) {
            is ApplicationResult.Applied -> result.value
            is ApplicationResult.Rejected -> return result
            is ApplicationResult.Unchanged -> error("Repair assessment cannot be unchanged")
        }
        val targetCount = divideRoundUp(
            Math.multiplyExact(assessment.totalEligibleCount, targetCompletionBasisPoints.toLong()),
            RepairJob.MAX_BASIS_POINTS.toLong(),
        )
        val needed = targetCount - assessment.restoredCount
        if (needed <= 0) {
            return ApplicationResult.Rejected(
                RepairTargetAlreadyReached(targetCompletionBasisPoints, assessment.restoredCount),
            )
        }
        if (needed > assessment.repairableCount) {
            return ApplicationResult.Rejected(
                RepairTargetUnreachable(
                    targetCompletionBasisPoints,
                    assessment.repairableCount,
                    assessment.conflictCount,
                ),
            )
        }
        val selected = assessment.entries
            .asSequence()
            .filter { it.condition == RepairCoordinateCondition.REPAIRABLE }
            .map(AssessedRepairChange::change)
            .map { it to selectionHash(basis, it.journalEntry.id) }
            .sortedWith { left, right ->
                compareUnsigned(left.second, right.second).takeIf { it != 0 }
                    ?: left.first.journalEntry.id.toString()
                        .compareTo(right.first.journalEntry.id.toString())
            }
            .take(needed.toInt())
            .map(Pair<ReportedBattleBlockChange, ByteArray>::first)
            .toList()
            .sortedWith(
                compareBy<ReportedBattleBlockChange>(
                    { it.journalEntry.position.worldId.value },
                    { it.journalEntry.position.y },
                    { it.journalEntry.position.x },
                    { it.journalEntry.position.z },
                    { it.journalEntry.id.toString() },
                ),
            )
        val restorePrice = rules.repair.restoreOriginalUnitPrice
        val removePrice = rules.repair.removePlacementUnitPrice
        val pricedGross = try {
            selected.fold(MoneyAmount.ZERO) { total, change ->
                total.plus(
                    when (change.reportEntry.costCategory) {
                        DamageCostCategory.RESTORE_ORIGINAL_BLOCK -> restorePrice
                        DamageCostCategory.REMOVE_PLACED_BLOCK -> removePrice
                        null -> error("Eligible repair change has no cost category")
                    },
                )
            }
        } catch (_: ArithmeticException) {
            return ApplicationResult.Rejected(EconomyAmountOverflow(basis.civilizationId))
        } catch (_: IllegalArgumentException) {
            return ApplicationResult.Rejected(EconomyAmountOverflow(basis.civilizationId))
        }
        val gross = if (fundingMode == RepairFundingMode.ADMIN_SPONSORED) {
            MoneyAmount.ZERO
        } else {
            pricedGross
        }
        val victor = basis.battle.winnerCivilizationId
            ?.takeIf { it != basis.civilizationId }
        val victorProceeds = if (fundingMode == RepairFundingMode.ADMIN_SPONSORED ||
            victor == null
        ) {
            MoneyAmount.ZERO
        } else {
            shareOf(gross, rules.repair.victorShareBasisPoints)
        }
        return ApplicationResult.Applied(
            RepairQuote(
                assessment = assessment,
                targetCompletionBasisPoints = targetCompletionBasisPoints,
                targetCompletionCount = targetCount,
                selectedChanges = selected,
                restoreOriginalUnitPrice = restorePrice,
                removePlacementUnitPrice = removePrice,
                grossCost = gross,
                victorShareBasisPoints = rules.repair.victorShareBasisPoints,
                victorCivilizationId = victor,
                victorProceeds = victorProceeds,
            ),
        )
    }

    private fun transition(
        jobId: RepairJobId,
        operation: RepairJob.() -> ApplicationResult<RepairJob>,
    ): ApplicationResult<RepairJob> = repository.transaction {
        val current = findRepairJob(jobId)
            ?: return@transaction ApplicationResult.Rejected(RepairJobNotFound(jobId))
        when (val result = current.operation()) {
            is ApplicationResult.Applied -> {
                updateRepairJob(result.value)
                result
            }
            is ApplicationResult.Unchanged,
            is ApplicationResult.Rejected,
            -> result
        }
    }

    private fun RepairJob.updated(
        newStatus: RepairJobStatus,
        terminal: Boolean = false,
        failure: String? = null,
    ): ApplicationResult<RepairJob> {
        val now = clock.instant()
        return ApplicationResult.Applied(
            copy(
                status = newStatus,
                updatedAt = now,
                completedAt = now.takeIf { terminal },
                failureMessage = failure,
            ),
        )
    }

    private fun CivilizationsReadContext.findPayment(job: RepairJob): LedgerTransaction? =
        job.paymentLedgerTransactionId?.let(::findLedgerTransaction)

    private fun RepairJob.matches(request: CreateRepairJobRequest): Boolean =
        battleId == request.battleId &&
            civilizationId == request.civilizationId &&
            initiatedByPlayerId == request.initiatedByPlayerId &&
            fundingMode == request.fundingMode &&
            idempotencyKey == request.idempotencyKey &&
            targetCompletionBasisPoints == request.targetCompletionBasisPoints

    private fun RepairQuote.unitPrice(change: ReportedBattleBlockChange): MoneyAmount =
        when (change.reportEntry.costCategory) {
            DamageCostCategory.RESTORE_ORIGINAL_BLOCK -> restoreOriginalUnitPrice
            DamageCostCategory.REMOVE_PLACED_BLOCK -> removePlacementUnitPrice
            null -> error("Eligible repair change has no cost category")
        }

    private fun selectionHash(basis: RepairAssessmentBasis, blockChangeId: BlockChangeId): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(
            "${basis.battle.id}:${basis.civilizationId}:$blockChangeId"
                .toByteArray(StandardCharsets.UTF_8),
        )

    companion object {
        private const val REPORT_PAGE_SIZE = 1_000
        private const val RECOVERY_BATCH_SIZE = 256
        private const val MAX_INSPECTION_PAGE_SIZE = 1_000
        const val MAX_WORK_BATCH_SIZE = 1_000

        private fun List<AssessedRepairChange>.countCondition(
            condition: RepairCoordinateCondition,
        ): Long = count { it.condition == condition }.toLong()

        private fun divideRoundUp(numerator: Long, denominator: Long): Long =
            numerator / denominator + if (numerator % denominator == 0L) 0 else 1

        private fun shareOf(amount: MoneyAmount, basisPoints: Int): MoneyAmount {
            val result = BigInteger.valueOf(amount.minorUnits)
                .multiply(BigInteger.valueOf(basisPoints.toLong()))
                .divide(BigInteger.valueOf(RepairJob.MAX_BASIS_POINTS.toLong()))
            return MoneyAmount(result.longValueExact())
        }

        private fun compareUnsigned(left: ByteArray, right: ByteArray): Int {
            for (index in left.indices) {
                val comparison = left[index].toUByte().compareTo(right[index].toUByte())
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
}

data class RepairAssessmentBasis(
    val battle: Battle,
    val civilizationId: CivilizationId,
    val eligibleChanges: List<ReportedBattleBlockChange>,
)

data class CurrentRepairObservation(
    val blockChangeId: BlockChangeId,
    val currentState: SimpleBlockSnapshot,
)

enum class RepairCoordinateCondition {
    RESTORED,
    REPAIRABLE,
    CONFLICT,
}

data class AssessedRepairChange(
    val change: ReportedBattleBlockChange,
    val condition: RepairCoordinateCondition,
)

data class RepairAssessment(
    val basis: RepairAssessmentBasis,
    val entries: List<AssessedRepairChange>,
    val restoredCount: Long,
    val repairableCount: Long,
    val conflictCount: Long,
) {
    val totalEligibleCount: Long = entries.size.toLong()
    val completionBasisPoints: Int =
        ((restoredCount * RepairJob.MAX_BASIS_POINTS) / totalEligibleCount).toInt()

    init {
        require(totalEligibleCount > 0)
        require(totalEligibleCount == restoredCount + repairableCount + conflictCount)
    }
}

data class QuoteRepairRequest(
    val battleId: BattleId,
    val civilizationId: CivilizationId,
    val targetCompletionBasisPoints: Int,
    val observations: List<CurrentRepairObservation>,
    val fundingMode: RepairFundingMode = RepairFundingMode.ORDINARY,
)

data class RepairQuote(
    val assessment: RepairAssessment,
    val targetCompletionBasisPoints: Int,
    val targetCompletionCount: Long,
    val selectedChanges: List<ReportedBattleBlockChange>,
    val restoreOriginalUnitPrice: MoneyAmount,
    val removePlacementUnitPrice: MoneyAmount,
    val grossCost: MoneyAmount,
    val victorShareBasisPoints: Int,
    val victorCivilizationId: CivilizationId?,
    val victorProceeds: MoneyAmount,
) {
    val selectedCount: Long = selectedChanges.size.toLong()
    val sinkAmount: MoneyAmount = grossCost.plus(victorProceeds.negate())
}

data class CreateRepairJobRequest(
    val battleId: BattleId,
    val civilizationId: CivilizationId,
    val initiatedByPlayerId: PlayerId?,
    val fundingMode: RepairFundingMode,
    val targetCompletionBasisPoints: Int,
    val observations: List<CurrentRepairObservation>,
    val idempotencyKey: String,
)

data class CreatedRepairJob(
    val job: RepairJob,
    val payment: LedgerTransaction?,
    /** Null only for an idempotent replay, whose original observations need not still be true. */
    val quote: RepairQuote?,
)

data class RepairWorkItem(
    val item: RepairJobItem,
    val change: ReportedBattleBlockChange,
)

data class RepairWorkBatch(
    val job: RepairJob,
    val items: List<RepairWorkItem>,
)

data class RecordRepairWorkBatch(
    val repairJobId: RepairJobId,
    val results: List<RepairWorkResult>,
)

data class RepairWorkResult(
    val blockChangeId: BlockChangeId,
    val ordinal: Long,
    val status: RepairJobItemStatus,
    val failureMessage: String? = null,
) {
    init {
        require(status != RepairJobItemStatus.PENDING) { "Work result cannot remain pending" }
        require(
            status == RepairJobItemStatus.FAILED || failureMessage == null,
        ) { "Only failed work may have a failure message" }
    }
}

data class RepairBattleNotFound(val battleId: BattleId) : ApplicationFailure {
    override val description: String = "Battle $battleId does not exist"
}

data class RepairBattleNotClosed(val battleId: BattleId) : ApplicationFailure {
    override val description: String = "Battle $battleId must be closed before damage can be repaired"
}

data class RepairCivilizationNotBattleParty(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId was not a party to this battle"
}

data class RepairCivilizationUnavailable(val civilizationId: CivilizationId) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId is unavailable for repair"
}

data class NoEligibleRepairDamage(
    val battleId: BattleId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId has no eligible damage owned by civilization $civilizationId"
}

data object RepairObservationCoverageMismatch : ApplicationFailure {
    override val description: String = "Current block observations must cover every eligible coordinate exactly once"
}

data class InvalidRepairTarget(val basisPoints: Int) : ApplicationFailure {
    override val description: String = "Repair target $basisPoints must be greater than 0% and at most 100%"
}

data class RepairTargetAlreadyReached(
    val targetBasisPoints: Int,
    val restoredCount: Long,
) : ApplicationFailure {
    override val description: String =
        "Repair target $targetBasisPoints is already reached ($restoredCount blocks restored)"
}

data class RepairTargetUnreachable(
    val targetBasisPoints: Int,
    val repairableCount: Long,
    val conflictCount: Long,
) : ApplicationFailure {
    override val description: String =
        "Repair target $targetBasisPoints cannot be reached: $repairableCount blocks are repairable and " +
            "$conflictCount have later player changes"
}

data class RepairConfirmationPriceExceeded(
    val confirmedMaximum: MoneyAmount,
    val currentPrice: MoneyAmount,
) : ApplicationFailure {
    override val description: String =
        "The current repair price exceeds the confirmed quote; review the new price"
}

data class RepairSeasonNotActive(val seasonId: SeasonId) : ApplicationFailure {
    override val description: String = "Season $seasonId is not active for new repairs"
}

data class OpenRepairJobAlreadyExists(
    val battleId: BattleId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Battle $battleId already has an open repair job for civilization $civilizationId"
}

data class RepairInitiatorMustBeMember(
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId must belong to civilization $civilizationId to start its repair"
}

data class OrdinaryRepairRequiresPlayer(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "An ordinary repair for civilization $civilizationId requires a player initiator"
}

data class RepairInitiatorRoleNotAllowed(
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId does not have a civilization role allowed to start repairs for $civilizationId"
}

data class RepairIdempotencyConflict(val key: String) : ApplicationFailure {
    override val description: String = "Repair idempotency key '$key' was already used differently"
}

data class InvalidRepairIdempotencyKey(val key: String) : ApplicationFailure {
    override val description: String =
        "Repair idempotency key must contain 1 through ${RepairJob.MAX_KEY_LENGTH} characters"
}

data class RepairJobNotFound(val jobId: RepairJobId) : ApplicationFailure {
    override val description: String = "Repair job $jobId does not exist"
}

data class InvalidRepairJobTransition(
    val jobId: RepairJobId,
    val status: RepairJobStatus,
    val operation: String,
) : ApplicationFailure {
    override val description: String = "Repair job $jobId is $status and cannot $operation"
}

data class InvalidRepairBatchSize(val size: Int) : ApplicationFailure {
    override val description: String =
        "Repair work batch size $size must be between 1 and ${RepairJobService.MAX_WORK_BATCH_SIZE}"
}

data class RepairBatchCursorConflict(val jobId: RepairJobId) : ApplicationFailure {
    override val description: String = "Repair job $jobId work results do not match its durable cursor"
}
