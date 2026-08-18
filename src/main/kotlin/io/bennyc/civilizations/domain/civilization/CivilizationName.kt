package io.bennyc.civilizations.domain.civilization

import java.util.Locale

@ConsistentCopyVisibility
data class CivilizationName private constructor(
    val value: String,
    val normalized: String,
) {
    companion object {
        fun from(raw: String): CivilizationName {
            val value = raw.trim()
            require(value.isNotBlank()) { "Civilization name cannot be blank" }
            require(value.length <= MAX_LENGTH) {
                "Civilization name cannot exceed $MAX_LENGTH characters"
            }
            return CivilizationName(value, value.lowercase(Locale.ROOT))
        }

        private const val MAX_LENGTH = 64
    }
}
