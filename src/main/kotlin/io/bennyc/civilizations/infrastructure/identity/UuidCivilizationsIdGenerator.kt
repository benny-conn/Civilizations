package io.bennyc.civilizations.infrastructure.identity

import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.util.UUID

class UuidCivilizationsIdGenerator : CivilizationsIdGenerator {
    override fun newSeasonId(): SeasonId = SeasonId(UUID.randomUUID())

    override fun newCivilizationId(): CivilizationId = CivilizationId(UUID.randomUUID())

    override fun newClaimId(): ClaimId = ClaimId(UUID.randomUUID())
}
