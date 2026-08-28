package io.bennyc.civilizations.application.economy

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationNotFound
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
import io.bennyc.civilizations.application.season.SeasonNotFound
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.economy.CivilizationAccount
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
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock

/** Durable civilization treasury and recoverable player-wallet bridge operations. */
class EconomyService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
    private val rules: EconomyRules,
) {
    private val ledger = EconomyLedger(idGenerator, clock)

    fun ensureSeasonAccounts(seasonId: SeasonId): ApplicationResult<SeasonEconomySettings> =
        repository.transaction {
            val season = findSeason(seasonId)
                ?: return@transaction ApplicationResult.Rejected(SeasonNotFound(seasonId))
            var settings = findSeasonEconomySettings(seasonId)
            if (settings == null) {
                settings = SeasonEconomySettings(
                    seasonId = seasonId,
                    currencyScale = rules.currencyScale,
                    openingBalance = rules.openingCivilizationBalance,
                    createdAt = clock.instant(),
                )
                insertSeasonEconomySettings(settings)
            } else if (settings.currencyScale != rules.currencyScale) {
                return@transaction ApplicationResult.Rejected(
                    EconomyCurrencyScaleConflict(
                        seasonId,
                        settings.currencyScale.decimalPlaces,
                        rules.currencyScale.decimalPlaces,
                    ),
                )
            }

            val now = clock.instant()
            for (civilization in listCivilizations(seasonId)) {
                if (findCivilizationAccount(civilization.id) != null) {
                    continue
                }
                insertCivilizationAccount(
                    CivilizationAccount(
                        seasonId = seasonId,
                        civilizationId = civilization.id,
                        balance = MoneyAmount.ZERO,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                val opening = ledger.post(
                    this,
                    LedgerTransactionRequest(
                        seasonId = seasonId,
                        idempotencyKey = "opening:$seasonId:${civilization.id}",
                        kind = LedgerTransactionKind.OPENING_BALANCE,
                        postings = listOf(
                            LedgerPosting(civilization.id, settings.openingBalance),
                        ),
                        referenceType = "CIVILIZATION",
                        referenceId = civilization.id.toString(),
                        actorPlayerId = null,
                        description = "Opening civilization treasury balance",
                    ),
                )
                check(opening !is ApplicationResult.Rejected) {
                    "Opening account ${civilization.id} failed: ${opening.failureDescription()}"
                }
            }
            ApplicationResult.Applied(settings)
        }

    fun post(request: LedgerTransactionRequest): ApplicationResult<LedgerTransaction> =
        repository.transaction {
            ledger.post(this, request)
        }

    fun preparePlayerDeposit(
        request: PreparePlayerEconomyTransfer,
    ): ApplicationResult<EconomyBridgeTransfer> = repository.transaction {
        prepareBridge(request, EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION)
    }

    fun preparePlayerWithdrawal(
        request: PreparePlayerEconomyTransfer,
    ): ApplicationResult<EconomyBridgeTransfer> = repository.transaction {
        prepareBridge(request, EconomyBridgeDirection.WITHDRAW_TO_PLAYER)
    }

    fun completeExternalTransfer(
        transferId: EconomyBridgeTransferId,
    ): ApplicationResult<EconomyBridgeTransfer> = repository.transaction {
        val current = findEconomyBridgeTransfer(transferId)
            ?: return@transaction ApplicationResult.Rejected(
                EconomyBridgeTransferNotFound(transferId),
            )
        if (current.status == EconomyBridgeStatus.COMPLETED) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.status != EconomyBridgeStatus.PREPARED) {
            return@transaction ApplicationResult.Rejected(
                InvalidEconomyBridgeTransition(transferId, current.status, "complete"),
            )
        }
        if (findSeasonEconomySettings(current.seasonId) == null) {
            return@transaction ApplicationResult.Rejected(
                EconomyNotInitialized(current.seasonId),
            )
        }
        val now = clock.instant()
        val ledgerId = when (current.direction) {
            EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION -> {
                val result = ledger.post(
                    this,
                    LedgerTransactionRequest(
                        seasonId = current.seasonId,
                        idempotencyKey = "bridge:${current.id}:deposit",
                        kind = LedgerTransactionKind.PLAYER_DEPOSIT,
                        postings = listOf(LedgerPosting(current.civilizationId, current.amount)),
                        referenceType = "ECONOMY_BRIDGE",
                        referenceId = current.id.toString(),
                        actorPlayerId = current.playerId,
                        description = "Player deposit into civilization treasury",
                    ),
                )
                result.valueOrThrow().id
            }
            EconomyBridgeDirection.WITHDRAW_TO_PLAYER ->
                checkNotNull(current.ledgerTransactionId)
        }
        val completed = current.copy(
            status = EconomyBridgeStatus.COMPLETED,
            ledgerTransactionId = ledgerId,
            updatedAt = now,
            completedAt = now,
        )
        updateEconomyBridgeTransfer(completed)
        ApplicationResult.Applied(completed)
    }

    fun failExternalTransfer(
        transferId: EconomyBridgeTransferId,
        message: String,
    ): ApplicationResult<EconomyBridgeTransfer> = repository.transaction {
        val current = findEconomyBridgeTransfer(transferId)
            ?: return@transaction ApplicationResult.Rejected(
                EconomyBridgeTransferNotFound(transferId),
            )
        if (current.status == EconomyBridgeStatus.EXTERNAL_FAILED) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.status != EconomyBridgeStatus.PREPARED) {
            return@transaction ApplicationResult.Rejected(
                InvalidEconomyBridgeTransition(transferId, current.status, "fail"),
            )
        }
        if (findSeasonEconomySettings(current.seasonId) == null) {
            return@transaction ApplicationResult.Rejected(
                EconomyNotInitialized(current.seasonId),
            )
        }
        val now = clock.instant()
        val reversalId = reverseWithdrawalIfNeeded(current)
        val failed = current.copy(
            status = EconomyBridgeStatus.EXTERNAL_FAILED,
            reversalTransactionId = reversalId,
            failureMessage = message.take(EconomyBridgeTransfer.MAX_FAILURE_LENGTH),
            updatedAt = now,
            completedAt = now,
        )
        updateEconomyBridgeTransfer(failed)
        ApplicationResult.Applied(failed)
    }

    /** Records an indeterminate provider result without assuming whether money moved. */
    fun requireBridgeReconciliation(
        transferId: EconomyBridgeTransferId,
        message: String,
    ): ApplicationResult<EconomyBridgeTransfer> = repository.transaction {
        val current = findEconomyBridgeTransfer(transferId)
            ?: return@transaction ApplicationResult.Rejected(
                EconomyBridgeTransferNotFound(transferId),
            )
        if (current.status == EconomyBridgeStatus.RECONCILIATION_REQUIRED) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.status != EconomyBridgeStatus.PREPARED) {
            return@transaction ApplicationResult.Rejected(
                InvalidEconomyBridgeTransition(transferId, current.status, "require reconciliation"),
            )
        }
        val ambiguous = current.copy(
            status = EconomyBridgeStatus.RECONCILIATION_REQUIRED,
            failureMessage = message.take(EconomyBridgeTransfer.MAX_FAILURE_LENGTH),
            updatedAt = clock.instant(),
        )
        updateEconomyBridgeTransfer(ambiguous)
        ApplicationResult.Applied(ambiguous)
    }

    /** Marks every pre-crash external operation ambiguous; recovery never retries Vault blindly. */
    fun recoverInterruptedBridgeTransfers(): Int {
        var recovered = 0
        while (true) {
            val count = repository.transaction {
                val prepared = listEconomyBridgeTransfers(
                    statuses = setOf(EconomyBridgeStatus.PREPARED),
                    limit = RECOVERY_BATCH_SIZE,
                )
                val now = clock.instant()
                prepared.forEach { transfer ->
                    updateEconomyBridgeTransfer(
                        transfer.copy(
                            status = EconomyBridgeStatus.RECONCILIATION_REQUIRED,
                            failureMessage = "Server stopped before the external result was recorded",
                            updatedAt = now,
                        ),
                    )
                }
                prepared.size
            }
            recovered += count
            if (count < RECOVERY_BATCH_SIZE) {
                return recovered
            }
        }
    }

    fun reconcileBridgeTransfer(
        request: ReconcileEconomyBridgeTransfer,
    ): ApplicationResult<EconomyBridgeTransfer> = repository.transaction {
        val current = findEconomyBridgeTransfer(request.transferId)
            ?: return@transaction ApplicationResult.Rejected(
                EconomyBridgeTransferNotFound(request.transferId),
            )
        if (current.status != EconomyBridgeStatus.RECONCILIATION_REQUIRED) {
            return@transaction ApplicationResult.Rejected(
                InvalidEconomyBridgeTransition(current.id, current.status, "reconcile"),
            )
        }
        if (findSeasonEconomySettings(current.seasonId) == null) {
            return@transaction ApplicationResult.Rejected(
                EconomyNotInitialized(current.seasonId),
            )
        }
        val now = clock.instant()
        val note = "Reconciled by ${request.adminPlayerId ?: "console"}: ${request.reason}"
            .take(EconomyBridgeTransfer.MAX_FAILURE_LENGTH)
        val resolved = if (request.externalOperationSucceeded) {
            val ledgerId = when (current.direction) {
                EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION -> ledger.post(
                    this,
                    LedgerTransactionRequest(
                        seasonId = current.seasonId,
                        idempotencyKey = "bridge:${current.id}:deposit",
                        kind = LedgerTransactionKind.PLAYER_DEPOSIT,
                        postings = listOf(LedgerPosting(current.civilizationId, current.amount)),
                        referenceType = "ECONOMY_BRIDGE",
                        referenceId = current.id.toString(),
                        actorPlayerId = current.playerId,
                        description = "Reconciled player deposit into civilization treasury",
                    ),
                ).valueOrThrow().id
                EconomyBridgeDirection.WITHDRAW_TO_PLAYER ->
                    checkNotNull(current.ledgerTransactionId)
            }
            current.copy(
                status = EconomyBridgeStatus.COMPLETED,
                ledgerTransactionId = ledgerId,
                failureMessage = note,
                updatedAt = now,
                completedAt = now,
            )
        } else {
            val reversalId = reverseWithdrawalIfNeeded(current)
            current.copy(
                status = EconomyBridgeStatus.RECONCILED_CANCELLED,
                reversalTransactionId = reversalId,
                failureMessage = note,
                updatedAt = now,
                completedAt = now,
            )
        }
        updateEconomyBridgeTransfer(resolved)
        ApplicationResult.Applied(resolved)
    }

    fun listReconciliationRequired(limit: Int = 100): List<EconomyBridgeTransfer> =
        repository.read {
            listEconomyBridgeTransfers(
                statuses = setOf(EconomyBridgeStatus.RECONCILIATION_REQUIRED),
                limit = limit.coerceIn(1, MAX_INSPECTION_PAGE_SIZE),
            )
        }

    fun listLedger(
        civilizationId: CivilizationId,
        limit: Int = 20,
    ): List<LedgerTransaction> = repository.read {
        listLedgerTransactionsForCivilization(
            civilizationId,
            limit.coerceIn(1, MAX_INSPECTION_PAGE_SIZE),
        )
    }

    private fun CivilizationsWriteContext.prepareBridge(
        request: PreparePlayerEconomyTransfer,
        direction: EconomyBridgeDirection,
    ): ApplicationResult<EconomyBridgeTransfer> {
        if (request.amount.minorUnits <= 0) {
            return ApplicationResult.Rejected(InvalidEconomyAmount(request.amount))
        }
        if (request.providerName.isBlank()) {
            return ApplicationResult.Rejected(PlayerEconomyUnavailable)
        }
        findEconomyBridgeTransferByIdempotencyKey(request.idempotencyKey)?.let { existing ->
            return if (existing.matches(request, direction)) {
                ApplicationResult.Unchanged(existing)
            } else {
                ApplicationResult.Rejected(EconomyIdempotencyConflict(request.idempotencyKey))
            }
        }
        val activeSeasonId = findActiveSeasonId()
        if (activeSeasonId != request.seasonId) {
            return ApplicationResult.Rejected(EconomySeasonNotActive(request.seasonId))
        }
        val season = findSeason(request.seasonId)
            ?: return ApplicationResult.Rejected(SeasonNotFound(request.seasonId))
        if (season.status == SeasonStatus.ARCHIVED) {
            return ApplicationResult.Rejected(EconomySeasonClosed(request.seasonId))
        }
        val settings = findSeasonEconomySettings(request.seasonId)
            ?: return ApplicationResult.Rejected(EconomyNotInitialized(request.seasonId))
        if (request.providerFractionalDigits >= 0 &&
            settings.currencyScale.decimalPlaces > request.providerFractionalDigits
        ) {
            return ApplicationResult.Rejected(
                PlayerEconomyPrecisionTooSmall(
                    request.providerName,
                    request.providerFractionalDigits,
                    settings.currencyScale.decimalPlaces,
                ),
            )
        }
        val civilization = findCivilization(request.civilizationId)
            ?: return ApplicationResult.Rejected(CivilizationNotFound(request.civilizationId))
        if (civilization.seasonId != request.seasonId ||
            civilization.status == CivilizationStatus.DISSOLVED
        ) {
            return ApplicationResult.Rejected(EconomyCivilizationUnavailable(request.civilizationId))
        }
        val membership = findMembership(request.seasonId, request.playerId)
        if (membership?.civilizationId != request.civilizationId) {
            return ApplicationResult.Rejected(
                EconomyPlayerMustBeMember(request.playerId, request.civilizationId),
            )
        }
        if (direction == EconomyBridgeDirection.WITHDRAW_TO_PLAYER &&
            membership.role != MembershipRole.LEADER
        ) {
            return ApplicationResult.Rejected(
                EconomyWithdrawalRequiresLeader(request.playerId, request.civilizationId),
            )
        }
        findOpenEconomyBridgeTransferForPlayer(request.playerId)?.let { open ->
            return ApplicationResult.Rejected(
                PlayerHasOpenEconomyBridgeTransfer(request.playerId, open.id),
            )
        }
        val account = findCivilizationAccount(request.civilizationId)
            ?: return ApplicationResult.Rejected(EconomyAccountNotFound(request.civilizationId))
        val now = clock.instant()
        val transferId = idGenerator.newEconomyBridgeTransferId()
        val withdrawalLedgerId = if (direction == EconomyBridgeDirection.WITHDRAW_TO_PLAYER) {
            val hold = ledger.post(
                this,
                LedgerTransactionRequest(
                    seasonId = request.seasonId,
                    idempotencyKey = "bridge:$transferId:withdrawal",
                    kind = LedgerTransactionKind.PLAYER_WITHDRAWAL,
                    postings = listOf(LedgerPosting(request.civilizationId, request.amount.negate())),
                    referenceType = "PLAYER",
                    referenceId = request.playerId.toString(),
                    actorPlayerId = request.playerId,
                    description = "Civilization treasury withdrawal to player",
                ),
            )
            when (hold) {
                is ApplicationResult.Applied -> hold.value.id
                is ApplicationResult.Unchanged -> hold.value.id
                is ApplicationResult.Rejected -> return hold
            }
        } else {
            null
        }
        val transfer = EconomyBridgeTransfer(
            id = transferId,
            seasonId = request.seasonId,
            civilizationId = account.civilizationId,
            playerId = request.playerId,
            direction = direction,
            amount = request.amount,
            currencyScale = settings.currencyScale,
            providerName = request.providerName,
            idempotencyKey = request.idempotencyKey,
            status = EconomyBridgeStatus.PREPARED,
            ledgerTransactionId = withdrawalLedgerId,
            reversalTransactionId = null,
            failureMessage = null,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
        )
        insertEconomyBridgeTransfer(transfer)
        return ApplicationResult.Applied(transfer)
    }

    private fun CivilizationsWriteContext.reverseWithdrawalIfNeeded(
        current: EconomyBridgeTransfer,
    ): LedgerTransactionId? {
        if (current.direction != EconomyBridgeDirection.WITHDRAW_TO_PLAYER) {
            return null
        }
        val reversal = ledger.post(
            this,
            LedgerTransactionRequest(
                seasonId = current.seasonId,
                idempotencyKey = "bridge:${current.id}:withdrawal-reversal",
                kind = LedgerTransactionKind.PLAYER_WITHDRAWAL_REVERSAL,
                postings = listOf(LedgerPosting(current.civilizationId, current.amount)),
                referenceType = "ECONOMY_BRIDGE",
                referenceId = current.id.toString(),
                actorPlayerId = current.playerId,
                description = "Reversal of unsuccessful player withdrawal",
            ),
        ).valueOrThrow()
        return reversal.id
    }

    private fun EconomyBridgeTransfer.matches(
        request: PreparePlayerEconomyTransfer,
        expectedDirection: EconomyBridgeDirection,
    ): Boolean = seasonId == request.seasonId &&
        civilizationId == request.civilizationId &&
        playerId == request.playerId &&
        direction == expectedDirection &&
        amount == request.amount &&
        providerName == request.providerName

    private fun ApplicationResult<LedgerTransaction>.valueOrThrow(): LedgerTransaction = when (this) {
        is ApplicationResult.Applied -> value
        is ApplicationResult.Unchanged -> value
        is ApplicationResult.Rejected -> error(failure.description)
    }

    private fun ApplicationResult<*>.failureDescription(): String = when (this) {
        is ApplicationResult.Rejected -> failure.description
        else -> "none"
    }

    companion object {
        private const val RECOVERY_BATCH_SIZE = 256
        private const val MAX_INSPECTION_PAGE_SIZE = 1_000
    }
}

data class LedgerTransactionRequest(
    val seasonId: SeasonId,
    val idempotencyKey: String,
    val kind: LedgerTransactionKind,
    val postings: List<LedgerPosting>,
    val referenceType: String?,
    val referenceId: String?,
    val actorPlayerId: PlayerId?,
    val description: String,
)

data class PreparePlayerEconomyTransfer(
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val playerId: PlayerId,
    val amount: MoneyAmount,
    val providerName: String,
    val providerFractionalDigits: Int,
    val idempotencyKey: String,
)

data class ReconcileEconomyBridgeTransfer(
    val transferId: EconomyBridgeTransferId,
    val externalOperationSucceeded: Boolean,
    val adminPlayerId: PlayerId?,
    val reason: String,
) {
    init {
        require(reason.isNotBlank()) { "Economy reconciliation requires a reason" }
    }
}

data object InvalidLedgerPostings : ApplicationFailure {
    override val description: String = "Ledger postings must be non-empty and unique by civilization"
}

data object InvalidCivilizationTransferPostings : ApplicationFailure {
    override val description: String =
        "Civilization transfers require at least two postings whose amounts sum to zero"
}

data class InvalidLedgerMetadata(val detail: String) : ApplicationFailure {
    override val description: String = "Invalid ledger metadata: $detail"
}

data class EconomyCurrencyScaleConflict(
    val seasonId: SeasonId,
    val storedScale: Int,
    val configuredScale: Int,
) : ApplicationFailure {
    override val description: String =
        "Season $seasonId already uses currency scale $storedScale; configured scale is $configuredScale"
}

data class EconomyNotInitialized(val seasonId: SeasonId) : ApplicationFailure {
    override val description: String = "Economy is not initialized for season $seasonId"
}

data class EconomySeasonNotActive(val seasonId: SeasonId) : ApplicationFailure {
    override val description: String = "Season $seasonId is not the active season"
}

data class EconomySeasonClosed(val seasonId: SeasonId) : ApplicationFailure {
    override val description: String = "Economy operations are closed for archived season $seasonId"
}

data class EconomyCivilizationUnavailable(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId is unavailable for economy use"
}

data class EconomyAccountNotFound(val civilizationId: CivilizationId) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId has no treasury account"
}

data class EconomyIdempotencyConflict(val key: String) : ApplicationFailure {
    override val description: String = "Economy idempotency key '$key' was already used differently"
}

data class InvalidEconomyAmount(val amount: MoneyAmount) : ApplicationFailure {
    override val description: String = "Economy amount ${amount.minorUnits} must be positive"
}

data class EconomyAmountOverflow(val civilizationId: CivilizationId) : ApplicationFailure {
    override val description: String = "Economy balance for $civilizationId would overflow"
}

data class InsufficientCivilizationFunds(
    val civilizationId: CivilizationId,
    val available: MoneyAmount,
    val requested: MoneyAmount,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId has insufficient funds: " +
            "available=${available.minorUnits}, requested=${requested.minorUnits}"
}

data object PlayerEconomyUnavailable : ApplicationFailure {
    override val description: String = "No external player economy provider is available"
}

data class PlayerEconomyPrecisionTooSmall(
    val providerName: String,
    val providerFractionalDigits: Int,
    val requiredFractionalDigits: Int,
) : ApplicationFailure {
    override val description: String =
        "$providerName supports $providerFractionalDigits decimal places; " +
            "Civilizations requires $requiredFractionalDigits"
}

data class EconomyPlayerMustBeMember(
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId must belong to civilization $civilizationId"
}

data class EconomyWithdrawalRequiresLeader(
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId must lead civilization $civilizationId to withdraw treasury funds"
}

data class PlayerHasOpenEconomyBridgeTransfer(
    val playerId: PlayerId,
    val transferId: EconomyBridgeTransferId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId already has unfinished economy transfer $transferId"
}

data class EconomyBridgeTransferNotFound(
    val transferId: EconomyBridgeTransferId,
) : ApplicationFailure {
    override val description: String = "Economy bridge transfer $transferId does not exist"
}

data class InvalidEconomyBridgeTransition(
    val transferId: EconomyBridgeTransferId,
    val status: EconomyBridgeStatus,
    val operation: String,
) : ApplicationFailure {
    override val description: String =
        "Economy bridge transfer $transferId is $status and cannot $operation"
}
