package com.aioffer.radar.domain

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.testing.BASE_COMPARISON_KEY
import com.aioffer.radar.core.testing.testMoney
import com.aioffer.radar.core.testing.testOffer
import com.aioffer.radar.core.testing.testPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PriceDisplayModelFactoryTest {
    @Test
    fun yearlyPrice_keepsTotalSeparateFromMonthlyEquivalent() {
        val offer = testOffer(
            key = BASE_COMPARISON_KEY.copy(billingPeriod = BillingPeriod.YEARLY),
            price = testPrice(actualAmountMinor = 120_000, originalAmountMinor = 150_000)
        )

        val result = PriceDisplayModelFactory.create(offer)

        assertEquals(testMoney(120_000), result.totalAmount)
        assertEquals(testMoney(10_000), result.monthlyEquivalent)
        assertEquals(BillingPeriod.YEARLY, result.billingPeriod)
    }

    @Test
    fun monthlyPrice_hasSameTotalAndMonthlyEquivalent() {
        val result = PriceDisplayModelFactory.create(testPrice(10_000), BillingPeriod.MONTHLY)

        assertEquals(testMoney(10_000), result.totalAmount)
        assertEquals(testMoney(10_000), result.monthlyEquivalent)
    }

    @Test
    fun oneTimePrice_hasNoMonthlyEquivalent() {
        val result = PriceDisplayModelFactory.create(
            testPrice(10_000, monthlyEquivalent = testMoney(1_000)),
            BillingPeriod.ONE_TIME
        )

        assertNull(result.monthlyEquivalent)
    }

    @Test
    fun suppliedMonthlyEquivalent_isPreserved() {
        val result = PriceDisplayModelFactory.create(
            testPrice(120_000, monthlyEquivalent = testMoney(9_999)),
            BillingPeriod.YEARLY
        )

        assertEquals(testMoney(9_999), result.monthlyEquivalent)
        assertEquals(testMoney(120_000), result.totalAmount)
    }

    @Test
    fun offerPriceCurrencyMismatch_isRejected() {
        val offer = testOffer(price = testPrice(10_000, currency = Currency.USD))

        assertThrows(IllegalArgumentException::class.java) {
            PriceDisplayModelFactory.create(offer)
        }
    }

    @Test
    fun monthlyEquivalentCurrencyMismatch_isRejected() {
        val offer = testOffer(
            price = testPrice(
                actualAmountMinor = 120_000,
                monthlyEquivalent = testMoney(10_000, Currency.USD)
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            PriceDisplayModelFactory.create(offer)
        }
    }

    @Test
    fun calculatedMonthlyEquivalent_roundsToMinorUnit() {
        val result = PriceDisplayModelFactory.create(testPrice(10_006), BillingPeriod.YEARLY)

        assertEquals(testMoney(834), result.monthlyEquivalent)
        assertEquals(testMoney(10_006), result.totalAmount)
    }
}
