package io.bennyc.civilizations.application.identity

import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId

interface CivilizationsIdGenerator {
    fun newSeasonId(): SeasonId

    fun newCivilizationId(): CivilizationId

    fun newClaimId(): ClaimId
}
