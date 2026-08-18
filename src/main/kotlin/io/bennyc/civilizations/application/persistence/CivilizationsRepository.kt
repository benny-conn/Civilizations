package io.bennyc.civilizations.application.persistence

import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.BlockChangeCursor
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleParticipant
import io.bennyc.civilizations.domain.war.War
import io.bennyc.civilizations.domain.war.WarId

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
    fun findActiveSeasonId(): SeasonId?

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

    fun findWar(id: WarId): War?

    fun listWarsForSeason(seasonId: SeasonId): List<War>

    fun listOpenWarsForCivilization(civilizationId: CivilizationId): List<War>

    fun findBattle(id: BattleId): Battle?

    fun listBattlesForWar(warId: WarId): List<Battle>

    fun listBattlesForSeason(seasonId: SeasonId): List<Battle>

    fun listOpenBattlesForCivilization(civilizationId: CivilizationId): List<Battle>

    fun listBattleParticipants(battleId: BattleId): List<BattleParticipant>

    fun findBlockChange(battleId: BattleId, position: BlockPosition3D): BattleBlockChange?

    fun countBlockChanges(battleId: BattleId): Long

    fun listBlockChanges(
        battleId: BattleId,
        after: BlockChangeCursor?,
        limit: Int,
    ): List<BattleBlockChange>
}

interface CivilizationsWriteContext : CivilizationsReadContext {
    fun setActiveSeasonId(seasonId: SeasonId?)

    fun insertSeason(season: Season)

    fun updateSeason(season: Season)

    fun insertCivilization(civilization: Civilization)

    fun updateCivilization(civilization: Civilization)

    fun insertMembership(membership: Membership)

    fun updateMembership(membership: Membership)

    fun deleteMembership(seasonId: SeasonId, playerId: PlayerId): Boolean

    fun insertClaim(claim: Claim)

    fun deleteClaim(id: ClaimId): Boolean

    fun insertWar(war: War)

    fun updateWar(war: War)

    fun insertBattle(battle: Battle)

    fun updateBattle(battle: Battle)

    fun insertBattleParticipant(participant: BattleParticipant)

    /** Returns false only when this battle/coordinate was already journaled. */
    fun insertBlockChangeIfAbsent(blockChange: BattleBlockChange): Boolean
}

class PersistenceRecordNotFoundException(message: String) : IllegalStateException(message)
