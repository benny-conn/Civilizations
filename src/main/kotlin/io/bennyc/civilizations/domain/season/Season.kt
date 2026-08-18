package io.bennyc.civilizations.domain.season

import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant

data class Season(
    val id: SeasonId,
    val name: String,
    val status: SeasonStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Season name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Season name cannot exceed $MAX_NAME_LENGTH characters" }
        require(name == name.trim()) { "Season name cannot have surrounding whitespace" }
        require(updatedAt >= createdAt) { "Season updatedAt cannot precede createdAt" }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 64
    }
}
