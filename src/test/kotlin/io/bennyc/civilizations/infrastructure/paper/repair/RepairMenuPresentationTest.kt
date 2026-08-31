package io.bennyc.civilizations.infrastructure.paper.repair

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.repair.RepairAssessment
import io.bennyc.civilizations.application.repair.RepairQuote
import io.bennyc.civilizations.application.repair.RepairTargetAlreadyReached
import io.bennyc.civilizations.application.repair.RepairTargetUnreachable
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class RepairMenuPresentationTest {
    @Test
    fun `fixed target slots remain absolute percentages`() {
        assertEquals(
            mapOf(28 to 2_500, 30 to 5_000, 32 to 7_500, 34 to 10_000),
            RepairMenuLayout.targetBySlot,
        )
    }

    @Test
    fun `battle pages are bounded and preserve order`() {
        val battles = (0 until 101).toList()

        assertEquals(3, RepairMenuLayout.pageCount(battles.size))
        assertEquals((0 until 45).toList(), RepairMenuLayout.page(battles, 0))
        assertEquals((45 until 90).toList(), RepairMenuLayout.page(battles, 1))
        assertEquals((90 until 101).toList(), RepairMenuLayout.page(battles, 2))
        assertEquals((90 until 101).toList(), RepairMenuLayout.page(battles, 99))
    }

    @Test
    fun `authoritative available quote is passed through unchanged`() {
        val quote = mock(RepairQuote::class.java)
        val status = status(ApplicationResult.Applied(quote))

        val presentation = assertIs<RepairQuotePresentation.Available>(status.presentQuote())

        assertSame(quote, presentation.quote)
    }

    @Test
    fun `settled target failures have distinct non-payable presentations`() {
        val reached = status(
            ApplicationResult.Rejected(RepairTargetAlreadyReached(5_000, 12)),
        ).presentQuote()
        val unreachable = status(
            ApplicationResult.Rejected(RepairTargetUnreachable(10_000, 3, 2)),
        ).presentQuote()

        assertEquals(
            RepairQuotePresentation.AlreadyReached(5_000, 12),
            reached,
        )
        assertEquals(
            RepairQuotePresentation.Unreachable(10_000, 3, 2),
            unreachable,
        )
    }

    @Test
    fun `unexpected application rejection remains visible to the menu`() {
        val failure = object : ApplicationFailure {
            override val description: String = "not allowed"
        }

        assertEquals(
            RepairQuotePresentation.Rejected("not allowed"),
            status(ApplicationResult.Rejected(failure)).presentQuote(),
        )
    }

    private fun status(quote: ApplicationResult<RepairQuote>): PaperRepairStatus =
        PaperRepairStatus(
            assessment = mock(RepairAssessment::class.java),
            targetCompletionBasisPoints = 10_000,
            quote = quote,
            jobs = emptyList(),
        )
}
