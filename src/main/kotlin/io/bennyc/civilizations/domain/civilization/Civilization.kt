package io.bennyc.civilizations.domain.civilization

import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant

data class Civilization(
    val id: CivilizationId,
    val seasonId: SeasonId,
    val name: CivilizationName,
    val status: CivilizationStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(updatedAt >= createdAt) { "Civilization updatedAt cannot precede createdAt" }
    }
}
