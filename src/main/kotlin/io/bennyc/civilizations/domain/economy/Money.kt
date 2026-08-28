package io.bennyc.civilizations.domain.economy

import java.math.BigDecimal
import java.math.RoundingMode

/** Exact fixed-point money stored as configured minor units, never as floating point. */
@JvmInline
value class MoneyAmount(val minorUnits: Long) {
    init {
        require(minorUnits in -MAX_ABSOLUTE_MINOR_UNITS..MAX_ABSOLUTE_MINOR_UNITS) {
            "Money amount exceeds the supported range"
        }
    }

    fun plus(other: MoneyAmount): MoneyAmount = MoneyAmount(Math.addExact(minorUnits, other.minorUnits))

    fun times(multiplier: Long): MoneyAmount = MoneyAmount(Math.multiplyExact(minorUnits, multiplier))

    fun negate(): MoneyAmount = MoneyAmount(Math.negateExact(minorUnits))

    companion object {
        val ZERO = MoneyAmount(0)
        const val MAX_ABSOLUTE_MINOR_UNITS = 9_000_000_000_000_000L
    }
}

@JvmInline
value class CurrencyScale(val decimalPlaces: Int) {
    init {
        require(decimalPlaces in MIN_DECIMAL_PLACES..MAX_DECIMAL_PLACES) {
            "Currency scale must be between $MIN_DECIMAL_PLACES and $MAX_DECIMAL_PLACES"
        }
    }

    fun parse(value: String): MoneyAmount {
        val decimal = try {
            BigDecimal(value.trim())
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("'$value' is not a valid money amount")
        }
        val minorUnits = try {
            decimal
                .setScale(decimalPlaces, RoundingMode.UNNECESSARY)
                .movePointRight(decimalPlaces)
                .longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException(
                "'$value' must fit the configured $decimalPlaces-decimal currency scale",
            )
        }
        return MoneyAmount(minorUnits)
    }

    fun format(amount: MoneyAmount): String =
        BigDecimal.valueOf(amount.minorUnits, decimalPlaces).toPlainString()

    fun toExternalDouble(amount: MoneyAmount): Double =
        BigDecimal.valueOf(amount.minorUnits, decimalPlaces).toDouble()

    companion object {
        const val MIN_DECIMAL_PLACES = 0
        const val MAX_DECIMAL_PLACES = 6
    }
}
