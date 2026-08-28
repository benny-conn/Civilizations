package io.bennyc.civilizations.domain.economy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun `currency scale parses and formats exact fixed point amounts`() {
        val scale = CurrencyScale(2)

        assertEquals(MoneyAmount(1_234), scale.parse("12.34"))
        assertEquals(MoneyAmount(-5), scale.parse("-0.05"))
        assertEquals("12.34", scale.format(MoneyAmount(1_234)))
        assertEquals(12.34, scale.toExternalDouble(MoneyAmount(1_234)))
    }

    @Test
    fun `currency scale rejects rounding and out of range values`() {
        val scale = CurrencyScale(2)

        assertFailsWith<IllegalArgumentException> { scale.parse("1.001") }
        assertFailsWith<IllegalArgumentException> { scale.parse("not-money") }
        assertFailsWith<IllegalArgumentException> {
            scale.parse("90000000000000.01")
        }
    }
}
