package io.bennyc.civilizations.application.economy

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationNotFound
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransaction
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.economy.SeasonEconomySettings
import java.time.Clock

/** Composable ledger writer for operations that must atomically persist their own records. */
class EconomyLedger(
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
) {
    fun post(
        context: CivilizationsWriteContext,
        request: LedgerTransactionRequest,
    ): ApplicationResult<LedgerTransaction> = with(context) {
        val settings = findSeasonEconomySettings(request.seasonId)
            ?: return ApplicationResult.Rejected(EconomyNotInitialized(request.seasonId))
        if (request.postings.isEmpty() ||
            request.postings.map(LedgerPosting::civilizationId).toSet().size !=
            request.postings.size
        ) {
            return ApplicationResult.Rejected(InvalidLedgerPostings)
        }
        findLedgerTransactionByIdempotencyKey(request.idempotencyKey)?.let { existing ->
            return if (existing.matches(request, settings)) {
                ApplicationResult.Unchanged(existing)
            } else {
                ApplicationResult.Rejected(EconomyIdempotencyConflict(request.idempotencyKey))
            }
        }
        if (request.kind == LedgerTransactionKind.CIVILIZATION_TRANSFER) {
            val total = request.postings.fold(MoneyAmount.ZERO) { sum, posting ->
                sum.plus(posting.amount)
            }
            if (total != MoneyAmount.ZERO || request.postings.size < 2) {
                return ApplicationResult.Rejected(InvalidCivilizationTransferPostings)
            }
        }
        for (posting in request.postings) {
            val civilization = findCivilization(posting.civilizationId)
                ?: return ApplicationResult.Rejected(
                    CivilizationNotFound(posting.civilizationId),
                )
            if (civilization.seasonId != request.seasonId) {
                return ApplicationResult.Rejected(
                    EconomyCivilizationUnavailable(posting.civilizationId),
                )
            }
            val account = findCivilizationAccount(posting.civilizationId)
                ?: return ApplicationResult.Rejected(
                    EconomyAccountNotFound(posting.civilizationId),
                )
            val resultingBalance = try {
                account.balance.plus(posting.amount)
            } catch (_: ArithmeticException) {
                return ApplicationResult.Rejected(EconomyAmountOverflow(posting.civilizationId))
            } catch (_: IllegalArgumentException) {
                return ApplicationResult.Rejected(EconomyAmountOverflow(posting.civilizationId))
            }
            if (resultingBalance.minorUnits < 0) {
                return ApplicationResult.Rejected(
                    InsufficientCivilizationFunds(
                        posting.civilizationId,
                        account.balance,
                        posting.amount.negate(),
                    ),
                )
            }
        }
        val transaction = try {
            LedgerTransaction(
                id = idGenerator.newLedgerTransactionId(),
                seasonId = request.seasonId,
                idempotencyKey = request.idempotencyKey,
                kind = request.kind,
                referenceType = request.referenceType,
                referenceId = request.referenceId,
                actorPlayerId = request.actorPlayerId,
                description = request.description,
                currencyScale = settings.currencyScale,
                createdAt = clock.instant(),
                postings = request.postings,
            )
        } catch (failure: IllegalArgumentException) {
            return ApplicationResult.Rejected(InvalidLedgerMetadata(failure.message.orEmpty()))
        }
        insertLedgerTransaction(transaction)
        ApplicationResult.Applied(transaction)
    }

    private fun LedgerTransaction.matches(
        request: LedgerTransactionRequest,
        settings: SeasonEconomySettings,
    ): Boolean = seasonId == request.seasonId &&
        idempotencyKey == request.idempotencyKey &&
        kind == request.kind &&
        referenceType == request.referenceType &&
        referenceId == request.referenceId &&
        actorPlayerId == request.actorPlayerId &&
        description == request.description &&
        currencyScale == settings.currencyScale &&
        postings == request.postings
}
