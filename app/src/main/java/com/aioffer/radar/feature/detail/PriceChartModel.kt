package com.aioffer.radar.feature.detail

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.domain.PriceDisplayModelFactory
import java.time.Instant

enum class ChartValueSemantics {
    ACTUAL_PRICE,
    MONTHLY_EQUIVALENT
}

data class PriceChartPoint(
    val observedAt: Instant,
    val value: Money
)

data class PriceChartModel(
    val points: List<PriceChartPoint>,
    val semantics: ChartValueSemantics,
    val currency: Currency,
    val highest: PriceChartPoint,
    val lowest: PriceChartPoint,
    val startAt: Instant,
    val endAt: Instant
) {
    val hasEnoughData: Boolean = points.map { it.observedAt }.distinct().size >= 2

    companion object {
        fun create(
            snapshots: List<PriceSnapshot>,
            billingPeriod: BillingPeriod
        ): PriceChartModel? {
            val orderedSnapshots = snapshots.sortedBy { it.observedAt }
            val first = orderedSnapshots.firstOrNull() ?: return null
            val usesMonthlyEquivalent = billingPeriod == BillingPeriod.QUARTERLY ||
                billingPeriod == BillingPeriod.YEARLY
            val points = orderedSnapshots.map { snapshot ->
                val value = if (usesMonthlyEquivalent) {
                    PriceDisplayModelFactory.create(snapshot.price, billingPeriod).monthlyEquivalent
                        ?: snapshot.price.actualAmount
                } else {
                    snapshot.price.actualAmount
                }
                PriceChartPoint(snapshot.observedAt, value)
            }
            return PriceChartModel(
                points = points,
                semantics = if (usesMonthlyEquivalent) {
                    ChartValueSemantics.MONTHLY_EQUIVALENT
                } else {
                    ChartValueSemantics.ACTUAL_PRICE
                },
                currency = first.comparisonKey.currency,
                highest = points.maxBy { it.value.amountMinor },
                lowest = points.minBy { it.value.amountMinor },
                startAt = points.first().observedAt,
                endAt = points.last().observedAt
            )
        }
    }
}
