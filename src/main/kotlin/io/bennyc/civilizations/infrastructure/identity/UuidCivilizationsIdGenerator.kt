package io.bennyc.civilizations.infrastructure.identity

import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransferId
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.WarId
import java.util.UUID

class UuidCivilizationsIdGenerator : CivilizationsIdGenerator {
    override fun newSeasonId(): SeasonId = SeasonId(UUID.randomUUID())

    override fun newCivilizationId(): CivilizationId = CivilizationId(UUID.randomUUID())

    override fun newClaimId(): ClaimId = ClaimId(UUID.randomUUID())

    override fun newWarId(): WarId = WarId(UUID.randomUUID())

    override fun newBattleId(): BattleId = BattleId(UUID.randomUUID())

    override fun newBlockChangeId(): BlockChangeId = BlockChangeId(UUID.randomUUID())

    override fun newLedgerTransactionId(): LedgerTransactionId =
        LedgerTransactionId(UUID.randomUUID())

    override fun newEconomyBridgeTransferId(): EconomyBridgeTransferId =
        EconomyBridgeTransferId(UUID.randomUUID())
}
