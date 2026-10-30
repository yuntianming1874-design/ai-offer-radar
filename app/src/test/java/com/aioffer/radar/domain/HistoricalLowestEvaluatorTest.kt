package com.aioffer.radar.domain

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.PlanType
import com.aioffer.radar.core.model.Region
import com.aioffer.radar.core.testing.BASE_COMPARISON_KEY
import com.aioffer.radar.core.testing.testMoney
import com.aioffer.radar.core.testing.testOffer
import com.aioffer.radar.core.testing.testPrice
import com.aioffer.radar.core.testing.testSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalLowestEvaluatorTest {
    @Test
    fun belowAllPaidHistory_isHistoricalLowest() {
        val candidate = testOffer(price = testPrice(8_000))
        val history = listOf(
            testSnapshot(id = "higher-1", price = testPrice(9_000)),
            testSnapshot(id = "higher-2", price = testPrice(10_000))
        )

        assertTrue(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }

    @Test
    fun equalToExistingLowest_isHistoricalLowest() {
        val candidate = testOffer(price = testPrice(8_000))
        val history = listOf(testSnapshot(price = testPrice(8_000)))

        assertTrue(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }

    @Test
    fun aboveExistingLowest_isNotHistoricalLowest() {
        val candidate = testOffer(price = testPrice(11_000))
        val history = listOf(testSnapshot(price = testPrice(10_000)))

        assertFalse(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }

    @Test
    fun candidateCurrencyMismatch_isNotHistoricalLowest() {
        val candidate = testOffer(
            key = BASE_COMPARISON_KEY.copy(currency = Currency.USD),
            price = testPrice(1_000)
        )
        val history = listOf(testSnapshot(price = testPrice(2_000)))

        assertFalse(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }

    @Test
    fun withoutHistory_isNotHistoricalLowest() {
        val candidate = testOffer(price = testPrice(10_000))

        assertFalse(HistoricalLowestEvaluator.isHistoricalLowest(candidate, emptyList()))
    }

    @Test
    fun otherComparisonGroupsOnly_areNotHistoricalLowest() {
        val otherKeys = listOf(
            BASE_COMPARISON_KEY.copy(productId = "glm"),
            BASE_COMPARISON_KEY.copy(planId = "kimi-student"),
            BASE_COMPARISON_KEY.copy(region = Region("HK")),
            BASE_COMPARISON_KEY.copy(currency = Currency.USD),
            BASE_COMPARISON_KEY.copy(billingPeriod = BillingPeriod.YEARLY),
            BASE_COMPARISON_KEY.copy(taxIncluded = false)
        )
        val history = otherKeys.mapIndexed { index, key ->
            testSnapshot(id = "other-$index", key = key, price = testPrice(1))
        }

        assertFalse(
            HistoricalLowestEvaluator.isHistoricalLowest(testOffer(price = testPrice(10_000)), history)
        )
    }

    @Test
    fun freeTrialCandidate_isNotMarkedAsPaidHistoricalLowest() {
        val candidate = testOffer(
            key = BASE_COMPARISON_KEY.copy(planId = "kimi-trial"),
            planType = PlanType.FREE_TRIAL,
            price = testPrice(1)
        )

        assertFalse(HistoricalLowestEvaluator.isHistoricalLowest(candidate, emptyList()))
    }

    @Test
    fun onlyFreeAndNegativeHistory_isNotPaidHistoricalLowest() {
        val candidate = testOffer(price = testPrice(10_000))
        val history = listOf(
            testSnapshot(id = "free", price = testPrice(0)),
            testSnapshot(id = "negative", price = testPrice(-100))
        )

        assertFalse(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }

    @Test
    fun freeAndNegativeHistory_doNotRaisePaidLowest() {
        val candidate = testOffer(price = testPrice(10_000))
        val history = listOf(
            testSnapshot(id = "paid", price = testPrice(11_000)),
            testSnapshot(id = "free", price = testPrice(0)),
            testSnapshot(id = "negative", price = testPrice(-100))
        )

        assertTrue(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }

    @Test
    fun onlyYearlyHistory_isNotMonthlyHistoricalLowest() {
        val candidate = testOffer(price = testPrice(10_000))
        val yearlySnapshot = testSnapshot(
            key = BASE_COMPARISON_KEY.copy(billingPeriod = BillingPeriod.YEARLY),
            price = testPrice(
                actualAmountMinor = 120_000,
                monthlyEquivalent = testMoney(5_000)
            )
        )

        assertFalse(HistoricalLowestEvaluator.isHistoricalLowest(candidate, listOf(yearlySnapshot)))
    }

    @Test
    fun yearlyHistory_doesNotPolluteMonthlyHistoricalLowest() {
        val candidate = testOffer(price = testPrice(10_000))
        val history = listOf(
            testSnapshot(id = "monthly", price = testPrice(10_000)),
            testSnapshot(
                id = "yearly",
                key = BASE_COMPARISON_KEY.copy(billingPeriod = BillingPeriod.YEARLY),
                price = testPrice(
                    actualAmountMinor = 60_000,
                    monthlyEquivalent = testMoney(5_000)
                )
            )
        )

        assertTrue(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }

    @Test
    fun trialHistory_doesNotLowerPaidHistoricalLowest() {
        val candidate = testOffer(price = testPrice(10_000))
        val history = listOf(
            testSnapshot(id = "paid", price = testPrice(12_000)),
            testSnapshot(
                id = "trial",
                key = BASE_COMPARISON_KEY.copy(planId = "kimi-trial"),
                planType = PlanType.FREE_TRIAL,
                price = testPrice(1)
            )
        )

        assertTrue(HistoricalLowestEvaluator.isHistoricalLowest(candidate, history))
    }
}
