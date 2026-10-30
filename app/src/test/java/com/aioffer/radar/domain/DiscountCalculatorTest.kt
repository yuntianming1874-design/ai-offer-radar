package com.aioffer.radar.domain

import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.testing.testMoney
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountCalculatorTest {
    @Test
    fun normalReduction_calculatesFraction() {
        val result = DiscountCalculator.calculate(testMoney(10_000), testMoney(7_500))

        assertTrue(result is DiscountResult.Calculated)
        result as DiscountResult.Calculated
        assertEquals(0, BigDecimal("0.25").compareTo(result.fraction))
        assertEquals(PriceDirection.DECREASE, result.direction)
    }

    @Test
    fun zeroOriginalPrice_isUnavailable() {
        val result = DiscountCalculator.calculate(testMoney(0), testMoney(0))

        assertEquals(
            DiscountResult.Unavailable(DiscountUnavailableReason.ZERO_ORIGINAL_PRICE),
            result
        )
    }

    @Test
    fun unchangedPrice_hasZeroDiscount() {
        val result = DiscountCalculator.calculate(testMoney(10_000), testMoney(10_000))

        assertTrue(result is DiscountResult.Calculated)
        result as DiscountResult.Calculated
        assertEquals(0, BigDecimal.ZERO.compareTo(result.fraction))
        assertEquals(PriceDirection.UNCHANGED, result.direction)
    }

    @Test
    fun priceIncrease_hasNegativeDiscount() {
        val result = DiscountCalculator.calculate(testMoney(10_000), testMoney(12_000))

        assertTrue(result is DiscountResult.Calculated)
        result as DiscountResult.Calculated
        assertEquals(0, BigDecimal("-0.2").compareTo(result.fraction))
        assertEquals(PriceDirection.INCREASE, result.direction)
    }

    @Test
    fun negativeOriginalAmount_isUnavailable() {
        val result = DiscountCalculator.calculate(testMoney(-1), testMoney(10_000))

        assertEquals(
            DiscountResult.Unavailable(DiscountUnavailableReason.NEGATIVE_AMOUNT),
            result
        )
    }

    @Test
    fun negativeCurrentAmount_isUnavailable() {
        val result = DiscountCalculator.calculate(testMoney(10_000), testMoney(-1))

        assertEquals(
            DiscountResult.Unavailable(DiscountUnavailableReason.NEGATIVE_AMOUNT),
            result
        )
    }

    @Test
    fun zeroCurrentPrice_isFullDiscount() {
        val result = DiscountCalculator.calculate(testMoney(10_000), testMoney(0))

        assertTrue(result is DiscountResult.Calculated)
        result as DiscountResult.Calculated
        assertEquals(0, BigDecimal.ONE.compareTo(result.fraction))
        assertEquals(PriceDirection.DECREASE, result.direction)
    }

    @Test
    fun differentCurrencies_areUnavailable() {
        val result = DiscountCalculator.calculate(
            testMoney(10_000, Currency.CNY),
            testMoney(7_500, Currency.USD)
        )

        assertEquals(
            DiscountResult.Unavailable(DiscountUnavailableReason.CURRENCY_MISMATCH),
            result
        )
    }
}
