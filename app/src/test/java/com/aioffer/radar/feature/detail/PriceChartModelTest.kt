package com.aioffer.radar.feature.detail

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.testing.BASE_COMPARISON_KEY
import com.aioffer.radar.core.testing.testPrice
import com.aioffer.radar.core.testing.testSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceChartModelTest {
    @Test
    fun create_sortsSnapshotsAndUsesActualPrices() {
        val later = testSnapshot(
            id = "later",
            price = testPrice(12_000),
            observedAt = Instant.parse("2026-02-01T00:00:00Z")
        )
        val earlier = testSnapshot(
            id = "earlier",
            price = testPrice(10_000),
            observedAt = Instant.parse("2026-01-01T00:00:00Z")
        )

        val chart = requireNotNull(
            PriceChartModel.create(listOf(later, earlier), BillingPeriod.MONTHLY)
        )

        assertEquals(ChartValueSemantics.ACTUAL_PRICE, chart.semantics)
        assertEquals(listOf(earlier.observedAt, later.observedAt), chart.points.map { it.observedAt })
        assertEquals(listOf(10_000L, 12_000L), chart.points.map { it.value.amountMinor })
        assertEquals(10_000L, chart.lowest.value.amountMinor)
        assertEquals(12_000L, chart.highest.value.amountMinor)
        assertTrue(chart.hasEnoughData)
    }

    @Test
    fun create_yearlySnapshotsUsesMonthlyEquivalent() {
        val key = BASE_COMPARISON_KEY.copy(billingPeriod = BillingPeriod.YEARLY)
        val calculated = testSnapshot(
            id = "calculated",
            key = key,
            price = testPrice(12_000),
            observedAt = Instant.parse("2026-02-01T00:00:00Z")
        )
        val supplied = testSnapshot(
            id = "supplied",
            key = key,
            price = testPrice(
                actualAmountMinor = 24_000,
                monthlyEquivalent = com.aioffer.radar.core.testing.testMoney(1_500)
            ),
            observedAt = Instant.parse("2026-01-01T00:00:00Z")
        )

        val chart = requireNotNull(
            PriceChartModel.create(listOf(calculated, supplied), BillingPeriod.YEARLY)
        )

        assertEquals(ChartValueSemantics.MONTHLY_EQUIVALENT, chart.semantics)
        assertEquals(listOf(1_500L, 1_000L), chart.points.map { it.value.amountMinor })
    }

    @Test
    fun create_singleTimestampDoesNotHaveEnoughData() {
        val observedAt = Instant.parse("2026-01-01T00:00:00Z")
        val snapshots = listOf(
            testSnapshot(id = "first", observedAt = observedAt),
            testSnapshot(id = "second", observedAt = observedAt)
        )

        val chart = requireNotNull(PriceChartModel.create(snapshots, BillingPeriod.MONTHLY))

        assertFalse(chart.hasEnoughData)
    }

    @Test
    fun create_emptyHistoryReturnsNull() {
        assertNull(PriceChartModel.create(emptyList(), BillingPeriod.MONTHLY))
    }
}
