package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.claim.ClaimRules
import org.bukkit.configuration.file.FileConfiguration
import java.nio.file.Path

data class CivilizationsConfiguration(
    val databasePath: Path,
    val claimRules: ClaimRules,
) {
    companion object {
        fun load(
            dataFolder: Path,
            config: FileConfiguration,
        ): CivilizationsConfiguration {
            val normalizedDataFolder = dataFolder.toAbsolutePath().normalize()
            val configuredFile = config.getString("v2.database-file")
                ?: error("Missing v2.database-file")
            require(configuredFile.isNotBlank()) { "v2.database-file cannot be blank" }
            val databasePath = normalizedDataFolder.resolve(configuredFile).normalize()
            require(databasePath.parent == normalizedDataFolder) {
                "v2.database-file must be a file directly inside the plugin data folder"
            }

            return CivilizationsConfiguration(
                databasePath = databasePath,
                claimRules = ClaimRules(
                    maxArea = config.getLong("v2.claims.max-area"),
                    maxClaimsPerCivilization = config.getInt("v2.claims.max-count"),
                    requireEdgeConnection = config.getBoolean(
                        "v2.claims.require-edge-connection",
                    ),
                ),
            )
        }
    }
}
