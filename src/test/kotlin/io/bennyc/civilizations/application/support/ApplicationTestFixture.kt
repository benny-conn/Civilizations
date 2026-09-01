package io.bennyc.civilizations.application.support

import io.bennyc.civilizations.application.ApplicationResult
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
import kotlin.test.fail

internal class SequentialIdGenerator : CivilizationsIdGenerator {
    private var season = 0L
    private var civilization = 100L
    private var claim = 200L
    private var claimGroup = 250L
    private var war = 300L
    private var battle = 400L
    private var blockChange = 500L
    private var ledgerTransaction = 600L
    private var economyBridgeTransfer = 700L
    private var repairJob = 800L
    private var landProtection = 900L

    override fun newSeasonId(): SeasonId = SeasonId(UUID(0, ++season))

    override fun newCivilizationId(): CivilizationId = CivilizationId(UUID(0, ++civilization))

    override fun newClaimId(): ClaimId = ClaimId(UUID(0, ++claim))

    override fun newClaimGroupId(): ClaimGroupId = ClaimGroupId(UUID(0, ++claimGroup))

    override fun newWarId(): WarId = WarId(UUID(0, ++war))

    override fun newBattleId(): BattleId = BattleId(UUID(0, ++battle))

    override fun newBlockChangeId(): BlockChangeId = BlockChangeId(UUID(0, ++blockChange))

    override fun newLedgerTransactionId(): LedgerTransactionId =
        LedgerTransactionId(UUID(0, ++ledgerTransaction))

    override fun newEconomyBridgeTransferId(): EconomyBridgeTransferId =
        EconomyBridgeTransferId(UUID(0, ++economyBridgeTransfer))

    override fun newRepairJobId(): RepairJobId = RepairJobId(UUID(0, ++repairJob))

    override fun newLandExposureId() = LandExposureId(UUID(0, ++landProtection))

    override fun newLandUpkeepAssessmentId() =
        LandUpkeepAssessmentId(UUID(0, ++landProtection))

    override fun newExposureDamageSiteId() = ExposureDamageSiteId(UUID(0, ++landProtection))

    override fun newExposureDamageEventId() = ExposureDamageEventId(UUID(0, ++landProtection))

    override fun newProtectionRepairJobId() = ProtectionRepairJobId(UUID(0, ++landProtection))
}

internal fun playerId(value: Long) = io.bennyc.civilizations.domain.identity.PlayerId(UUID(1, value))

internal fun <T> ApplicationResult<T>.appliedValue(): T = when (this) {
    is ApplicationResult.Applied -> value
    is ApplicationResult.Rejected -> fail("Expected applied result, got ${failure.description}")
    is ApplicationResult.Unchanged -> fail("Expected applied result, got unchanged $value")
}

internal fun <T> ApplicationResult<T>.unchangedValue(): T = when (this) {
    is ApplicationResult.Applied -> fail("Expected unchanged result, got applied $value")
    is ApplicationResult.Rejected -> fail("Expected unchanged result, got ${failure.description}")
    is ApplicationResult.Unchanged -> value
}

internal fun ApplicationResult<*>.rejection() = when (this) {
    is ApplicationResult.Applied -> fail("Expected rejection, got applied $value")
    is ApplicationResult.Rejected -> failure
    is ApplicationResult.Unchanged -> fail("Expected rejection, got unchanged $value")
}
