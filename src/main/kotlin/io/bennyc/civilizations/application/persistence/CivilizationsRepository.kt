package io.bennyc.civilizations.application.persistence

import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BattleDamageReport
import io.bennyc.civilizations.domain.damage.BattleDamageReportEntry
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.BlockChangeCursor
import io.bennyc.civilizations.domain.damage.ReportedBattleBlockChange
import io.bennyc.civilizations.domain.economy.CivilizationAccount
import io.bennyc.civilizations.domain.economy.EconomyBridgeStatus
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransfer
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransferId
import io.bennyc.civilizations.domain.economy.LedgerTransaction
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.economy.SeasonEconomySettings
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.repair.RepairJob
import io.bennyc.civilizations.domain.repair.RepairJobId
import io.bennyc.civilizations.domain.repair.RepairJobItem
import io.bennyc.civilizations.domain.repair.RepairJobStatus
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleCombatState
import io.bennyc.civilizations.domain.war.BattleCombatant
import io.bennyc.civilizations.domain.war.BattleCasualty
import io.bennyc.civilizations.domain.war.BattleCasualtyEconomics
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleLifeEvent
import io.bennyc.civilizations.domain.war.BattleLifeEventId
import io.bennyc.civilizations.domain.war.BattleParticipant
import io.bennyc.civilizations.domain.war.BattleSurrenderRecord
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

    fun findBattleCombatState(battleId: BattleId): BattleCombatState?

    fun listBattleCombatStatesForSeason(seasonId: SeasonId): List<BattleCombatState>

    fun listBattleCombatants(battleId: BattleId): List<BattleCombatant>

    fun findBattleLifeEvent(id: BattleLifeEventId): BattleLifeEvent?

    fun listBattleLifeEvents(battleId: BattleId): List<BattleLifeEvent>

    fun findBattleCasualtyEconomics(battleId: BattleId): BattleCasualtyEconomics?

    fun listBattleCasualtyEconomicsForSeason(seasonId: SeasonId): List<BattleCasualtyEconomics>

    fun findBattleCasualty(lifeEventId: BattleLifeEventId): BattleCasualty?

    fun listBattleCasualties(battleId: BattleId): List<BattleCasualty>

    fun findBattleSurrender(battleId: BattleId): BattleSurrenderRecord?

    fun listBattleSurrendersForSeason(seasonId: SeasonId): List<BattleSurrenderRecord>

    fun findSeasonEconomySettings(seasonId: SeasonId): SeasonEconomySettings?

    fun findCivilizationAccount(civilizationId: CivilizationId): CivilizationAccount?

    fun listCivilizationAccounts(seasonId: SeasonId): List<CivilizationAccount>

    fun findLedgerTransaction(id: LedgerTransactionId): LedgerTransaction?

    fun findLedgerTransactionByIdempotencyKey(idempotencyKey: String): LedgerTransaction?

    fun listLedgerTransactionsForCivilization(
        civilizationId: CivilizationId,
        limit: Int,
    ): List<LedgerTransaction>

    fun findEconomyBridgeTransfer(id: EconomyBridgeTransferId): EconomyBridgeTransfer?

    fun findEconomyBridgeTransferByIdempotencyKey(
        idempotencyKey: String,
    ): EconomyBridgeTransfer?

    fun findOpenEconomyBridgeTransferForPlayer(playerId: PlayerId): EconomyBridgeTransfer?

    fun listEconomyBridgeTransfers(
        statuses: Set<EconomyBridgeStatus>,
        limit: Int,
    ): List<EconomyBridgeTransfer>

    fun findRepairJob(id: RepairJobId): RepairJob?

    fun findRepairJobByIdempotencyKey(idempotencyKey: String): RepairJob?

    fun findOpenRepairJob(battleId: BattleId, civilizationId: CivilizationId): RepairJob?

    fun listRepairJobsForBattle(battleId: BattleId, limit: Int): List<RepairJob>

    fun listRepairJobsForCivilization(
        civilizationId: CivilizationId,
        limit: Int,
    ): List<RepairJob>

    fun listRepairJobsByStatus(
        statuses: Set<RepairJobStatus>,
        limit: Int,
    ): List<RepairJob>

    fun listRepairJobItems(
        repairJobId: RepairJobId,
        afterOrdinal: Long?,
        limit: Int,
    ): List<RepairJobItem>

    fun findBlockChange(battleId: BattleId, position: BlockPosition3D): BattleBlockChange?

    fun countBlockChanges(battleId: BattleId): Long

    fun listBlockChanges(
        battleId: BattleId,
        after: BlockChangeCursor?,
        limit: Int,
    ): List<BattleBlockChange>

    fun findDamageReport(battleId: BattleId): BattleDamageReport?

    fun findReportedBlockChange(
        battleId: BattleId,
        blockChangeId: BlockChangeId,
    ): ReportedBattleBlockChange?

    fun listReportedBlockChanges(
        battleId: BattleId,
        after: BlockChangeCursor?,
        limit: Int,
    ): List<ReportedBattleBlockChange>
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

    fun insertBattleCombatState(state: BattleCombatState)

    fun updateBattleCombatState(state: BattleCombatState)

    fun insertBattleCombatant(combatant: BattleCombatant)

    fun updateBattleCombatant(combatant: BattleCombatant)

    fun insertBattleLifeEvent(event: BattleLifeEvent)

    fun insertBattleCasualtyEconomics(economics: BattleCasualtyEconomics)

    fun updateBattleCasualtyEconomics(economics: BattleCasualtyEconomics)

    fun insertBattleCasualty(casualty: BattleCasualty)

    fun insertBattleSurrender(surrender: BattleSurrenderRecord)

    fun insertSeasonEconomySettings(settings: SeasonEconomySettings)

    fun insertCivilizationAccount(account: CivilizationAccount)

    fun insertLedgerTransaction(transaction: LedgerTransaction)

    fun insertEconomyBridgeTransfer(transfer: EconomyBridgeTransfer)

    fun updateEconomyBridgeTransfer(transfer: EconomyBridgeTransfer)

    fun insertRepairJob(job: RepairJob)

    fun updateRepairJob(job: RepairJob)

    fun insertRepairJobItem(item: RepairJobItem)

    fun updateRepairJobItem(item: RepairJobItem)

    /** Returns false only when this battle/coordinate was already journaled. */
    fun insertBlockChangeIfAbsent(blockChange: BattleBlockChange): Boolean

    /** Report entries are staged before [insertDamageReport] seals the complete set. */
    fun insertDamageReportEntry(entry: BattleDamageReportEntry)

    fun insertDamageReport(report: BattleDamageReport)
}

class PersistenceRecordNotFoundException(message: String) : IllegalStateException(message)
