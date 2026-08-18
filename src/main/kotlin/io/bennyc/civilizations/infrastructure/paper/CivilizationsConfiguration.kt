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
            val configuredFile = config.getString(
                config.path("storage.database-file", "v2.database-file"),
            ) ?: error("Missing storage.database-file")
            require(configuredFile.isNotBlank()) { "storage.database-file cannot be blank" }
            val databasePath = normalizedDataFolder.resolve(configuredFile).normalize()
            require(databasePath.parent == normalizedDataFolder) {
                "storage.database-file must be a file directly inside the plugin data folder"
            }

            return CivilizationsConfiguration(
                databasePath = databasePath,
                claimRules = ClaimRules(
                    maxArea = config.getLong(config.path("claims.max-area", "v2.claims.max-area")),
                    maxClaimsPerCivilization = config.getInt(
                        config.path("claims.max-count", "v2.claims.max-count"),
                    ),
                    requireEdgeConnection = config.getBoolean(
                        config.path(
                            "claims.require-edge-connection",
                            "v2.claims.require-edge-connection",
                        ),
                    ),
                ),
            )
        }

        private fun FileConfiguration.path(
            current: String,
            previous: String,
        ): String = if (contains(current)) current else previous
    }
}
