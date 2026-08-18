package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.claim.ClaimRules
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.domain.season.SeasonStatus
import org.bukkit.configuration.file.FileConfiguration
import java.nio.file.Path

data class CivilizationsConfiguration(
    val databasePath: Path,
    val claimRules: ClaimRules,
    val phaseRules: GameplayPhaseRules,
) {
    companion object {
        fun load(
            dataFolder: Path,
            config: FileConfiguration,
        ): CivilizationsConfiguration {
            val normalizedDataFolder = dataFolder.toAbsolutePath().normalize()
            val configuredFile = config.requiredString(
                "storage.database-file",
                "v2.database-file",
            )
            require(configuredFile.isNotBlank()) { "storage.database-file cannot be blank" }
            val databasePath = normalizedDataFolder.resolve(configuredFile).normalize()
            require(databasePath.parent == normalizedDataFolder) {
                "storage.database-file must be a file directly inside the plugin data folder"
            }
            val maxClaimArea = config.requiredLong("claims.max-area", "v2.claims.max-area")
            require(maxClaimArea > 0) { "claims.max-area must be positive" }
            val maxClaimCount = config.requiredInt("claims.max-count", "v2.claims.max-count")
            require(maxClaimCount > 0) { "claims.max-count must be positive" }

            return CivilizationsConfiguration(
                databasePath = databasePath,
                claimRules = ClaimRules(
                    maxArea = maxClaimArea,
                    maxClaimsPerCivilization = maxClaimCount,
                    requireEdgeConnection = config.requiredBoolean(
                        "claims.require-edge-connection",
                        "v2.claims.require-edge-connection",
                    ),
                ),
                phaseRules = GameplayPhaseRules(
                    rosterChangesAllowedIn = config.requiredPhases(
                        path = "gameplay.phase-gates.roster-changes",
                        safePhases = ROSTER_AND_CLAIM_PHASES,
                    ),
                    claimCreationAllowedIn = config.requiredPhases(
                        path = "gameplay.phase-gates.claim-creation",
                        safePhases = ROSTER_AND_CLAIM_PHASES,
                    ),
                    memberLandActionsAllowedIn = config.requiredPhases(
                        path = "gameplay.phase-gates.member-land-actions",
                        safePhases = MEMBER_LAND_ACTION_PHASES,
                    ),
                ),
            )
        }

        private fun FileConfiguration.path(
            current: String,
            previous: String? = null,
        ): String = when {
            contains(current, true) -> current
            previous != null && contains(previous, true) -> previous
            else -> current
        }

        private fun FileConfiguration.requiredString(
            current: String,
            previous: String? = null,
        ): String {
            val path = path(current, previous)
            return get(path) as? String
                ?: throw IllegalArgumentException("$current must be a string")
        }

        private fun FileConfiguration.requiredLong(
            current: String,
            previous: String? = null,
        ): Long {
            val path = path(current, previous)
            return when (val value = get(path)) {
                is Byte -> value.toLong()
                is Short -> value.toLong()
                is Int -> value.toLong()
                is Long -> value
                else -> throw IllegalArgumentException("$current must be an integer")
            }
        }

        private fun FileConfiguration.requiredInt(
            current: String,
            previous: String? = null,
        ): Int {
            val value = requiredLong(current, previous)
            require(value in Int.MIN_VALUE..Int.MAX_VALUE) {
                "$current must fit in a 32-bit integer"
            }
            return value.toInt()
        }

        private fun FileConfiguration.requiredBoolean(
            current: String,
            previous: String? = null,
        ): Boolean {
            val path = path(current, previous)
            return get(path) as? Boolean
                ?: throw IllegalArgumentException("$current must be true or false")
        }

        private fun FileConfiguration.requiredPhases(
            path: String,
            safePhases: Set<SeasonStatus>,
        ): Set<SeasonStatus> {
            val rawValues = get(path) as? List<*>
                ?: throw IllegalArgumentException("$path must be a YAML list")
            val phases = rawValues.mapIndexed { index, rawValue ->
                val value = rawValue as? String
                    ?: throw IllegalArgumentException("$path[$index] must be a phase name")
                SeasonStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw IllegalArgumentException(
                        "$path[$index] must be one of ${SeasonStatus.entries.joinToString()}",
                    )
            }
            require(phases.size == phases.toSet().size) { "$path cannot contain duplicate phases" }
            val unsafe = phases.filterNot(safePhases::contains)
            require(unsafe.isEmpty()) {
                "$path cannot enable ${unsafe.joinToString()}; safe values are " +
                    safePhases.joinToString()
            }
            return phases.toSet()
        }

        private val ROSTER_AND_CLAIM_PHASES =
            setOf(SeasonStatus.SETUP, SeasonStatus.PEACE)
        private val MEMBER_LAND_ACTION_PHASES =
            ROSTER_AND_CLAIM_PHASES + SeasonStatus.WAR
    }
}
