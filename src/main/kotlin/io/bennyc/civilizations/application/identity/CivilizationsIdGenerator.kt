package io.bennyc.civilizations.application.identity

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

interface CivilizationsIdGenerator {
    fun newSeasonId(): SeasonId

    fun newCivilizationId(): CivilizationId

    fun newClaimId(): ClaimId

    fun newClaimGroupId(): ClaimGroupId

    fun newWarId(): WarId

    fun newBattleId(): BattleId

    fun newBlockChangeId(): BlockChangeId

    fun newLedgerTransactionId(): LedgerTransactionId

    fun newEconomyBridgeTransferId(): EconomyBridgeTransferId

    fun newRepairJobId(): RepairJobId

    fun newLandExposureId(): LandExposureId

    fun newLandUpkeepAssessmentId(): LandUpkeepAssessmentId

    fun newExposureDamageSiteId(): ExposureDamageSiteId

    fun newExposureDamageEventId(): ExposureDamageEventId

    fun newProtectionRepairJobId(): ProtectionRepairJobId
}
