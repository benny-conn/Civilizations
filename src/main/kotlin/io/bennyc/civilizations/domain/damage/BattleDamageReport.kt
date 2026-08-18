package io.bennyc.civilizations.domain.damage

import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.BattleId
import java.time.Instant

/**
 * The immutable resolution-time summary for one battle's journal.
 *
 * A repair unit is deliberately not currency. Season rules may later assign
 * prices to the two cost categories without changing which coordinates were
 * eligible when this report was sealed.
 */
data class BattleDamageReport(
    val seasonId: SeasonId,
    val battleId: BattleId,
    val journaledChangeCount: Long,
    val eligibleChangeCount: Long,
    val restoredDuringBattleCount: Long,
    val restoreOriginalBlockCount: Long,
    val removePlacedBlockCount: Long,
    val generatedAt: Instant,
) {
    init {
        require(journaledChangeCount >= 0) { "Journaled change count cannot be negative" }
        require(eligibleChangeCount >= 0) { "Eligible change count cannot be negative" }
        require(restoredDuringBattleCount >= 0) {
            "Restored-during-battle count cannot be negative"
        }
        require(restoreOriginalBlockCount >= 0) {
            "Restore-original count cannot be negative"
        }
        require(removePlacedBlockCount >= 0) {
            "Remove-placed count cannot be negative"
        }
        require(journaledChangeCount == eligibleChangeCount + restoredDuringBattleCount) {
            "Every journaled change must be eligible or already restored"
        }
        require(eligibleChangeCount == restoreOriginalBlockCount + removePlacedBlockCount) {
            "Every eligible change must have exactly one cost category"
        }
    }

    /** Neutral one-coordinate units; monetary pricing is a later rules snapshot. */
    val baseRepairUnitCount: Long
        get() = eligibleChangeCount
}

enum class DamageReportEligibility {
    ELIGIBLE,
    RESTORED_DURING_BATTLE,
}

enum class DamageCostCategory {
    RESTORE_ORIGINAL_BLOCK,
    REMOVE_PLACED_BLOCK,
}

/** The final observation and frozen eligibility decision for one journal row. */
data class BattleDamageReportEntry(
    val seasonId: SeasonId,
    val battleId: BattleId,
    val blockChangeId: BlockChangeId,
    val finalState: SimpleBlockSnapshot,
    val eligibility: DamageReportEligibility,
    val costCategory: DamageCostCategory?,
) {
    init {
        when (eligibility) {
            DamageReportEligibility.ELIGIBLE -> requireNotNull(costCategory) {
                "An eligible damage entry requires a cost category"
            }
            DamageReportEligibility.RESTORED_DURING_BATTLE -> require(costCategory == null) {
                "An already-restored damage entry cannot have a cost category"
            }
        }
    }
}

/** A report entry joined to its immutable reconstruction source. */
data class ReportedBattleBlockChange(
    val journalEntry: BattleBlockChange,
    val reportEntry: BattleDamageReportEntry,
) {
    init {
        require(journalEntry.seasonId == reportEntry.seasonId) {
            "Reported change season does not match its journal entry"
        }
        require(journalEntry.battleId == reportEntry.battleId) {
            "Reported change battle does not match its journal entry"
        }
        require(journalEntry.id == reportEntry.blockChangeId) {
            "Reported change ID does not match its journal entry"
        }
    }

    val cursor: BlockChangeCursor
        get() = journalEntry.cursor
}
