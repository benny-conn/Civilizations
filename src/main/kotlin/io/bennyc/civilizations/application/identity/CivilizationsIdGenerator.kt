package io.bennyc.civilizations.application.identity

import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransferId
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.WarId

interface CivilizationsIdGenerator {
    fun newSeasonId(): SeasonId

    fun newCivilizationId(): CivilizationId

    fun newClaimId(): ClaimId

    fun newWarId(): WarId

    fun newBattleId(): BattleId

    fun newBlockChangeId(): BlockChangeId

    fun newLedgerTransactionId(): LedgerTransactionId

    fun newEconomyBridgeTransferId(): EconomyBridgeTransferId
}
