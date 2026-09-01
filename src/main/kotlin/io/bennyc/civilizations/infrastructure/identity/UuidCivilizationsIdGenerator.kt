package io.bennyc.civilizations.infrastructure.identity

import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.ClaimGroupId
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransferId
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.repair.RepairJobId
import io.bennyc.civilizations.domain.protection.ExposureDamageEventId
import io.bennyc.civilizations.domain.protection.ExposureDamageSiteId
import io.bennyc.civilizations.domain.protection.LandExposureId
import io.bennyc.civilizations.domain.protection.LandUpkeepAssessmentId
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobId
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.WarId
import java.util.UUID

class UuidCivilizationsIdGenerator : CivilizationsIdGenerator {
    override fun newSeasonId(): SeasonId = SeasonId(UUID.randomUUID())

    override fun newCivilizationId(): CivilizationId = CivilizationId(UUID.randomUUID())

    override fun newClaimId(): ClaimId = ClaimId(UUID.randomUUID())

    override fun newClaimGroupId(): ClaimGroupId = ClaimGroupId(UUID.randomUUID())

    override fun newWarId(): WarId = WarId(UUID.randomUUID())

    override fun newBattleId(): BattleId = BattleId(UUID.randomUUID())

    override fun newBlockChangeId(): BlockChangeId = BlockChangeId(UUID.randomUUID())

    override fun newLedgerTransactionId(): LedgerTransactionId =
        LedgerTransactionId(UUID.randomUUID())

    override fun newEconomyBridgeTransferId(): EconomyBridgeTransferId =
        EconomyBridgeTransferId(UUID.randomUUID())

    override fun newRepairJobId(): RepairJobId = RepairJobId(UUID.randomUUID())

    override fun newLandExposureId() = LandExposureId(UUID.randomUUID())

    override fun newLandUpkeepAssessmentId() = LandUpkeepAssessmentId(UUID.randomUUID())

    override fun newExposureDamageSiteId() = ExposureDamageSiteId(UUID.randomUUID())

    override fun newExposureDamageEventId() = ExposureDamageEventId(UUID.randomUUID())

    override fun newProtectionRepairJobId() = ProtectionRepairJobId(UUID.randomUUID())
}
