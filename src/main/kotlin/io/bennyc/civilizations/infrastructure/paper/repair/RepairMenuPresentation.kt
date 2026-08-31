package io.bennyc.civilizations.infrastructure.paper.repair

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.repair.RepairQuote
import io.bennyc.civilizations.application.repair.RepairTargetAlreadyReached
import io.bennyc.civilizations.application.repair.RepairTargetUnreachable

/** Fixed inventory layout and framework-neutral translation of authoritative quote results. */
internal object RepairMenuLayout {
    const val BATTLE_PAGE_SIZE = 45
    const val PREVIOUS_PAGE_SLOT = 45
    const val BACK_SLOT = 45
    const val REFRESH_SLOT = 49
    const val NEXT_PAGE_SLOT = 53
    const val CLOSE_SLOT = 53
    const val CONFIRM_SLOT = 31

    val targetBySlot: Map<Int, Int> = linkedMapOf(
        28 to 2_500,
        30 to 5_000,
        32 to 7_500,
        34 to 10_000,
    )

    fun pageCount(itemCount: Int): Int =
        maxOf(1, (itemCount + BATTLE_PAGE_SIZE - 1) / BATTLE_PAGE_SIZE)

    fun <T> page(items: List<T>, page: Int): List<T> {
        val safePage = page.coerceIn(0, pageCount(items.size) - 1)
        val from = safePage * BATTLE_PAGE_SIZE
        return items.subList(from, minOf(from + BATTLE_PAGE_SIZE, items.size))
    }
}

internal sealed interface RepairQuotePresentation {
    data class Available(val quote: RepairQuote) : RepairQuotePresentation

    data class AlreadyReached(
        val targetBasisPoints: Int,
        val restoredCount: Long,
    ) : RepairQuotePresentation

    data class Unreachable(
        val targetBasisPoints: Int,
        val repairableCount: Long,
        val conflictCount: Long,
    ) : RepairQuotePresentation

    data class Rejected(val description: String) : RepairQuotePresentation
}

internal fun PaperRepairStatus.presentQuote(): RepairQuotePresentation = when (val result = quote) {
    is ApplicationResult.Applied -> RepairQuotePresentation.Available(result.value)
    is ApplicationResult.Unchanged -> RepairQuotePresentation.Available(result.value)
    is ApplicationResult.Rejected -> when (val failure = result.failure) {
        is RepairTargetAlreadyReached -> RepairQuotePresentation.AlreadyReached(
            targetBasisPoints = failure.targetBasisPoints,
            restoredCount = failure.restoredCount,
        )
        is RepairTargetUnreachable -> RepairQuotePresentation.Unreachable(
            targetBasisPoints = failure.targetBasisPoints,
            repairableCount = failure.repairableCount,
            conflictCount = failure.conflictCount,
        )
        else -> RepairQuotePresentation.Rejected(failure.description)
    }
}
