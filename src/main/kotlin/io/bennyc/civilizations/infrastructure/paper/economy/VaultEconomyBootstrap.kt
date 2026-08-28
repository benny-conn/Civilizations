package io.bennyc.civilizations.infrastructure.paper.economy

import io.bennyc.civilizations.application.economy.PlayerEconomyGateway
import net.milkbowl.vault.economy.Economy
import org.bukkit.Server

/** Loaded only after Paper confirms Vault is present, keeping Vault genuinely optional. */
object VaultEconomyBootstrap {
    fun discover(server: Server): PlayerEconomyGateway? {
        val provider = server.servicesManager.getRegistration(Economy::class.java)?.provider
            ?: return null
        return VaultPlayerEconomyGateway(server, provider)
    }
}
