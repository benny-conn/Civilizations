package io.bennyc.civilizations.domain.claim

import io.bennyc.civilizations.domain.identity.CivilizationId

data class Claim(
    val id: ClaimId,
    val civilizationId: CivilizationId,
    val bounds: ClaimBounds,
)
