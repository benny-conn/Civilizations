package io.bennyc.civilizations.domain.civilization

import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant

data class Membership(
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val playerId: PlayerId,
    val role: MembershipRole,
    val joinedAt: Instant,
)
