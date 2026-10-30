package com.aioffer.radar.domain

import com.aioffer.radar.core.model.Money
import java.math.BigDecimal
import java.math.MathContext

enum class PriceDirection {
    DECREASE,
    UNCHANGED,
    INCREASE
}

enum class DiscountUnavailableReason {
    NEGATIVE_AMOUNT,
    CURRENCY_MISMATCH,
    ZERO_ORIGINAL_PRICE
}

sealed interface DiscountResult {
    data class Calculated(
        val fraction: BigDecimal,
        val direction: PriceDirection
    ) : DiscountResult

    data class Unavailable(
        val reason: DiscountUnavailableReason
    ) : DiscountResult
}

object DiscountCalculator {
    fun calculate(original: Money, current: Money): DiscountResult {
        if (original.isNegative || current.isNegative) {
            return DiscountResult.Unavailable(DiscountUnavailableReason.NEGATIVE_AMOUNT)
        }
        if (original.currency != current.currency) {
            return DiscountResult.Unavailable(DiscountUnavailableReason.CURRENCY_MISMATCH)
        }
        if (original.amountMinor == 0L) {
            return DiscountResult.Unavailable(DiscountUnavailableReason.ZERO_ORIGINAL_PRICE)
        }

        val difference = BigDecimal.valueOf(original.amountMinor)
            .subtract(BigDecimal.valueOf(current.amountMinor))
        val fraction = difference.divide(
            BigDecimal.valueOf(original.amountMinor),
            MathContext.DECIMAL128
        )
        val direction = when {
            current.amountMinor < original.amountMinor -> PriceDirection.DECREASE
            current.amountMinor > original.amountMinor -> PriceDirection.INCREASE
            else -> PriceDirection.UNCHANGED
        }
        return DiscountResult.Calculated(fraction, direction)
    }
}
