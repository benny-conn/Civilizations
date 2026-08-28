package io.bennyc.civilizations.infrastructure.paper.economy

import io.bennyc.civilizations.application.economy.PlayerEconomyDescriptor
import io.bennyc.civilizations.application.economy.PlayerEconomyGateway
import io.bennyc.civilizations.application.economy.PlayerEconomyResult
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.PlayerId
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.Server

/** The only adapter allowed to translate exact Civilizations money into Vault doubles. */
class VaultPlayerEconomyGateway(
    private val server: Server,
    private val economy: Economy,
) : PlayerEconomyGateway {
    override val descriptor = PlayerEconomyDescriptor(
        providerName = economy.name,
        fractionalDigits = economy.fractionalDigits(),
    )

    override fun withdraw(
        playerId: PlayerId,
        amount: MoneyAmount,
        currencyScale: CurrencyScale,
    ): PlayerEconomyResult {
        requireServerThread()
        return economy.withdrawPlayer(
            server.getOfflinePlayer(playerId.value),
            currencyScale.toExternalDouble(amount),
        ).toResult()
    }

    override fun deposit(
        playerId: PlayerId,
        amount: MoneyAmount,
        currencyScale: CurrencyScale,
    ): PlayerEconomyResult {
        requireServerThread()
        return economy.depositPlayer(
            server.getOfflinePlayer(playerId.value),
            currencyScale.toExternalDouble(amount),
        ).toResult()
    }

    private fun requireServerThread() {
        check(Bukkit.isPrimaryThread()) { "Vault economy mutations must run on the server thread" }
    }

    private fun EconomyResponse.toResult(): PlayerEconomyResult = if (transactionSuccess()) {
        PlayerEconomyResult.Success(externalAmount = amount, externalBalance = balance)
    } else {
        PlayerEconomyResult.Failure(errorMessage.ifBlank { "Vault rejected the transaction" })
    }
}
