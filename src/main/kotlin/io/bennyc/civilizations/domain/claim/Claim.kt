package io.bennyc.civilizations.domain.claim

import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId

data class Claim(
    val id: ClaimId,
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val bounds: ClaimBounds,
    /** Defaults only for source compatibility with pre-group fixtures. New writes are explicit. */
    val groupId: ClaimGroupId = ClaimGroupId(civilizationId.value),
)
