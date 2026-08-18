package io.bennyc.civilizations.application.persistence

import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.Season

/**
 * Durable storage port owned by the application layer.
 *
 * Implementations provide a fresh read context or one atomic write transaction.
 * Contexts are scoped to the callback and must not be retained by callers.
 */
interface CivilizationsRepository {
    fun <T> read(block: CivilizationsReadContext.() -> T): T

    fun <T> transaction(block: CivilizationsWriteContext.() -> T): T
}

interface CivilizationsReadContext {
    fun findSeason(id: SeasonId): Season?

    fun listSeasons(): List<Season>

    fun findCivilization(id: CivilizationId): Civilization?

    fun findCivilizationByName(seasonId: SeasonId, name: CivilizationName): Civilization?

    fun listCivilizations(seasonId: SeasonId): List<Civilization>

    fun findMembership(seasonId: SeasonId, playerId: PlayerId): Membership?

    fun listMemberships(civilizationId: CivilizationId): List<Membership>

    fun findClaim(id: ClaimId): Claim?

    fun listClaims(civilizationId: CivilizationId): List<Claim>

    fun listClaimsForSeason(seasonId: SeasonId): List<Claim>
}

interface CivilizationsWriteContext : CivilizationsReadContext {
    fun insertSeason(season: Season)

    fun updateSeason(season: Season)

    fun insertCivilization(civilization: Civilization)

    fun updateCivilization(civilization: Civilization)

    fun insertMembership(membership: Membership)

    fun updateMembership(membership: Membership)

    fun deleteMembership(seasonId: SeasonId, playerId: PlayerId): Boolean

    fun insertClaim(claim: Claim)

    fun deleteClaim(id: ClaimId): Boolean
}

class PersistenceRecordNotFoundException(message: String) : IllegalStateException(message)
