package io.bennyc.civilizations.infrastructure.paper.repair

import io.bennyc.civilizations.application.repair.RepairWorkItem
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BattleDamageReportEntry
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.DamageCostCategory
import io.bennyc.civilizations.domain.damage.DamageReportEligibility
import io.bennyc.civilizations.domain.damage.ReportedBattleBlockChange
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.repair.RepairJobId
import io.bennyc.civilizations.domain.repair.RepairJobItem
import io.bennyc.civilizations.domain.repair.RepairJobItemStatus
import io.bennyc.civilizations.domain.war.BattleId
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RepairBlockDecisionTest {
    private val original = SimpleBlockSnapshot("minecraft:stone")
    private val damaged = SimpleBlockSnapshot("minecraft:air")
    private val work = workItem(original, damaged)

    @Test
    fun `sealed damaged state restores the immutable original`() {
        val decision = assertIs<RepairBlockDecision.Restore>(
            RepairBlockDecision.decide(work, damaged),
        )

        assertEquals(original, decision.original)
    }

    @Test
    fun `manual exact restoration advances without another world mutation`() {
        assertEquals(
            RepairBlockDecision.AlreadyRestored,
            RepairBlockDecision.decide(work, original),
        )
    }

    @Test
    fun `later player edit is a conflict and is never selected for overwrite`() {
        assertEquals(
            RepairBlockDecision.Conflict,
            RepairBlockDecision.decide(work, SimpleBlockSnapshot("minecraft:granite")),
        )
    }

    private fun workItem(
        original: SimpleBlockSnapshot,
        damaged: SimpleBlockSnapshot,
    ): RepairWorkItem {
        val seasonId = SeasonId(id(1))
        val battleId = BattleId(id(2))
        val changeId = BlockChangeId(id(3))
        return RepairWorkItem(
            item = RepairJobItem(
                repairJobId = RepairJobId(id(4)),
                battleId = battleId,
                blockChangeId = changeId,
                ordinal = 0,
                unitPrice = MoneyAmount(100),
                status = RepairJobItemStatus.PENDING,
                processedAt = null,
                failureMessage = null,
            ),
            change = ReportedBattleBlockChange(
                journalEntry = BattleBlockChange(
                    id = changeId,
                    seasonId = seasonId,
                    battleId = battleId,
                    claimId = ClaimId(id(5)),
                    position = BlockPosition3D(
                        WorldId("minecraft:overworld"),
                        1,
                        64,
                        2,
                    ),
                    originalState = original,
                    firstMutationCause = BlockMutationCause.PLAYER_BREAK,
                    firstActorId = PlayerId(id(6)),
                    recordedAt = Instant.parse("2026-08-28T12:00:00Z"),
                ),
                reportEntry = BattleDamageReportEntry(
                    seasonId = seasonId,
                    battleId = battleId,
                    blockChangeId = changeId,
                    finalState = damaged,
                    eligibility = DamageReportEligibility.ELIGIBLE,
                    costCategory = DamageCostCategory.RESTORE_ORIGINAL_BLOCK,
                ),
            ),
        )
    }

    private fun id(value: Long): UUID = UUID(0, value)
}
