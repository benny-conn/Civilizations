package io.bennyc.civilizations.infrastructure.paper.economy

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.economy.PlayerEconomyGateway
import io.bennyc.civilizations.application.economy.PlayerEconomyResult
import io.bennyc.civilizations.application.economy.PreparePlayerEconomyTransfer
import io.bennyc.civilizations.domain.economy.EconomyBridgeDirection
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransfer
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/** Coordinates SQL prepare -> one Vault call -> SQL completion without ever blind-retrying Vault. */
class PaperEconomyBridgeCoordinator(
    private val runtime: CivilizationsRuntime,
    private val gateway: PlayerEconomyGateway?,
    private val logger: Logger,
) {
    fun transfer(
        player: Player,
        seasonId: SeasonId,
        civilizationId: CivilizationId,
        direction: EconomyBridgeDirection,
        amount: MoneyAmount,
        completion: (PaperEconomyTransferOutcome) -> Unit,
    ) {
        check(Bukkit.isPrimaryThread()) { "Economy bridge commands must begin on the server thread" }
        val availableGateway = gateway
            ?: return completion(PaperEconomyTransferOutcome.Rejected(
                "No Vault player-economy provider is available",
            ))
        val request = PreparePlayerEconomyTransfer(
            seasonId = seasonId,
            civilizationId = civilizationId,
            playerId = PlayerId(player.uniqueId),
            amount = amount,
            providerName = availableGateway.descriptor.providerName,
            providerFractionalDigits = availableGateway.descriptor.fractionalDigits,
            idempotencyKey = "player-command:${UUID.randomUUID()}",
        )
        runtime.submitMutation(
            operation = {
                when (direction) {
                    EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION ->
                        economy.preparePlayerDeposit(request)
                    EconomyBridgeDirection.WITHDRAW_TO_PLAYER ->
                        economy.preparePlayerWithdrawal(request)
                }
            },
            completion = { prepared -> handlePrepared(availableGateway, direction, prepared, completion) },
        )
    }

    private fun handlePrepared(
        availableGateway: PlayerEconomyGateway,
        direction: EconomyBridgeDirection,
        outcome: RuntimeMutationOutcome<EconomyBridgeTransfer>,
        completion: (PaperEconomyTransferOutcome) -> Unit,
    ) {
        when (outcome) {
            is RuntimeMutationOutcome.NotReady ->
                completion(PaperEconomyTransferOutcome.Rejected("Civilizations is not ready"))
            is RuntimeMutationOutcome.Failed ->
                completion(PaperEconomyTransferOutcome.Failed(outcome.failure))
            is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                is ApplicationResult.Rejected ->
                    completion(PaperEconomyTransferOutcome.Rejected(result.failure.description))
                is ApplicationResult.Unchanged -> completion(
                    PaperEconomyTransferOutcome.Rejected(
                        "Economy transfer ${result.value.id} was already prepared; it was not charged again",
                    ),
                )
                is ApplicationResult.Applied -> executeExternal(
                    availableGateway,
                    direction,
                    result.value,
                    completion,
                )
            }
        }
    }

    private fun executeExternal(
        availableGateway: PlayerEconomyGateway,
        direction: EconomyBridgeDirection,
        transfer: EconomyBridgeTransfer,
        completion: (PaperEconomyTransferOutcome) -> Unit,
    ) {
        check(Bukkit.isPrimaryThread()) { "Vault economy mutations must run on the server thread" }
        val external = try {
            ExternalAttempt.Definite(when (direction) {
                EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION -> availableGateway.withdraw(
                    transfer.playerId,
                    transfer.amount,
                    transfer.currencyScale,
                )
                EconomyBridgeDirection.WITHDRAW_TO_PLAYER -> availableGateway.deposit(
                    transfer.playerId,
                    transfer.amount,
                    transfer.currencyScale,
                )
            })
        } catch (failure: Throwable) {
            logger.log(Level.WARNING, "Vault call failed for bridge ${transfer.id}", failure)
            ExternalAttempt.Ambiguous(failure.message ?: failure::class.simpleName.orEmpty())
        }
        runtime.submitMutation(
            operation = {
                when (external) {
                    is ExternalAttempt.Ambiguous -> economy.requireBridgeReconciliation(
                        transfer.id,
                        "Vault threw before its result could be confirmed: ${external.message}",
                    )
                    is ExternalAttempt.Definite -> when (val result = external.result) {
                        is PlayerEconomyResult.Success -> economy.completeExternalTransfer(transfer.id)
                        is PlayerEconomyResult.Failure ->
                            economy.failExternalTransfer(transfer.id, result.message)
                    }
                }
            },
            completion = { finalized ->
                when (finalized) {
                    is RuntimeMutationOutcome.NotReady -> completion(
                        PaperEconomyTransferOutcome.ReconciliationRequired(transfer.id.toString()),
                    )
                    is RuntimeMutationOutcome.Failed -> {
                        logger.log(
                            Level.SEVERE,
                            "External economy result for bridge ${transfer.id} needs reconciliation",
                            finalized.failure,
                        )
                        completion(PaperEconomyTransferOutcome.ReconciliationRequired(transfer.id.toString()))
                    }
                    is RuntimeMutationOutcome.Completed -> when (val result = finalized.result) {
                        is ApplicationResult.Rejected -> completion(
                            PaperEconomyTransferOutcome.ReconciliationRequired(transfer.id.toString()),
                        )
                        is ApplicationResult.Applied ->
                            completeDefiniteOrAmbiguous(external, result.value, completion)
                        is ApplicationResult.Unchanged ->
                            completeDefiniteOrAmbiguous(external, result.value, completion)
                    }
                }
            },
        )
    }

    private fun completeDefiniteOrAmbiguous(
        attempt: ExternalAttempt,
        transfer: EconomyBridgeTransfer,
        completion: (PaperEconomyTransferOutcome) -> Unit,
    ) {
        when (attempt) {
            is ExternalAttempt.Ambiguous -> completion(
                PaperEconomyTransferOutcome.ReconciliationRequired(transfer.id.toString()),
            )
            is ExternalAttempt.Definite -> when (val result = attempt.result) {
                is PlayerEconomyResult.Success -> completion(
                    PaperEconomyTransferOutcome.Completed(transfer),
                )
                is PlayerEconomyResult.Failure -> completion(
                    PaperEconomyTransferOutcome.Rejected(result.message),
                )
            }
        }
    }

    private sealed interface ExternalAttempt {
        data class Definite(val result: PlayerEconomyResult) : ExternalAttempt
        data class Ambiguous(val message: String) : ExternalAttempt
    }
}

sealed interface PaperEconomyTransferOutcome {
    data class Completed(val transfer: EconomyBridgeTransfer) : PaperEconomyTransferOutcome
    data class Rejected(val description: String) : PaperEconomyTransferOutcome
    data class ReconciliationRequired(val transferId: String) : PaperEconomyTransferOutcome
    data class Failed(val failure: Throwable) : PaperEconomyTransferOutcome
}
