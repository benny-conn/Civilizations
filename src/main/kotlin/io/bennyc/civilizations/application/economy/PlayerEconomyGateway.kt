package io.bennyc.civilizations.application.economy

import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.PlayerId

/** Application-owned boundary for the server's external player-wallet provider. */
interface PlayerEconomyGateway {
    val descriptor: PlayerEconomyDescriptor

    fun withdraw(
        playerId: PlayerId,
        amount: MoneyAmount,
        currencyScale: CurrencyScale,
    ): PlayerEconomyResult

    fun deposit(
        playerId: PlayerId,
        amount: MoneyAmount,
        currencyScale: CurrencyScale,
    ): PlayerEconomyResult
}

data class PlayerEconomyDescriptor(
    val providerName: String,
    /** Negative means the provider does not publish a fixed precision. */
    val fractionalDigits: Int,
)

sealed interface PlayerEconomyResult {
    data class Success(
        val externalAmount: Double,
        val externalBalance: Double,
    ) : PlayerEconomyResult

    data class Failure(val message: String) : PlayerEconomyResult
}
