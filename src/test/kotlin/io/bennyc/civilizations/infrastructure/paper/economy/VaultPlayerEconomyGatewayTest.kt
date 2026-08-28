package io.bennyc.civilizations.infrastructure.paper.economy

import io.bennyc.civilizations.application.economy.PlayerEconomyResult
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.PlayerId
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.mockito.Mockito
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VaultPlayerEconomyGatewayTest {
    @Test
    fun `adapter performs one server-thread Vault call with scaled amount`() {
        val server = Mockito.mock(Server::class.java)
        val economy = Mockito.mock(Economy::class.java)
        val player = Mockito.mock(OfflinePlayer::class.java)
        val playerId = PlayerId(UUID(1, 2))
        Mockito.`when`(server.getOfflinePlayer(playerId.value)).thenReturn(player)
        Mockito.`when`(economy.name).thenReturn("Test Economy")
        Mockito.`when`(economy.fractionalDigits()).thenReturn(2)
        Mockito.`when`(economy.withdrawPlayer(player, 12.34)).thenReturn(
            EconomyResponse(12.34, 87.66, EconomyResponse.ResponseType.SUCCESS, ""),
        )

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Boolean> { Bukkit.isPrimaryThread() }.thenReturn(true)
            val gateway = VaultPlayerEconomyGateway(server, economy)

            val result = assertIs<PlayerEconomyResult.Success>(
                gateway.withdraw(playerId, MoneyAmount(1_234), CurrencyScale(2)),
            )

            assertEquals("Test Economy", gateway.descriptor.providerName)
            assertEquals(2, gateway.descriptor.fractionalDigits)
            assertEquals(12.34, result.externalAmount)
            assertEquals(87.66, result.externalBalance)
            Mockito.verify(economy).withdrawPlayer(player, 12.34)
        }
    }

    @Test
    fun `Vault rejection is returned as a definite failure`() {
        val server = Mockito.mock(Server::class.java)
        val economy = Mockito.mock(Economy::class.java)
        val player = Mockito.mock(OfflinePlayer::class.java)
        val playerId = PlayerId(UUID(1, 3))
        Mockito.`when`(server.getOfflinePlayer(playerId.value)).thenReturn(player)
        Mockito.`when`(economy.name).thenReturn("Test Economy")
        Mockito.`when`(economy.fractionalDigits()).thenReturn(2)
        Mockito.`when`(economy.depositPlayer(player, 5.0)).thenReturn(
            EconomyResponse(0.0, 10.0, EconomyResponse.ResponseType.FAILURE, "account locked"),
        )

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Boolean> { Bukkit.isPrimaryThread() }.thenReturn(true)
            val result = VaultPlayerEconomyGateway(server, economy).deposit(
                playerId,
                MoneyAmount(500),
                CurrencyScale(2),
            )

            assertEquals("account locked", assertIs<PlayerEconomyResult.Failure>(result).message)
        }
    }
}
