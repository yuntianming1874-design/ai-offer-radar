package com.aioffer.radar.domain

import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.PlanType
import com.aioffer.radar.core.model.PriceSnapshot

/**
 * Historical lows are only meaningful against at least one prior observation in the same
 * comparison group. Callers must not pass the snapshot created for the candidate itself,
 * otherwise a single observation would be compared with itself and always look like a low.
 */
object HistoricalLowestEvaluator {
    fun isHistoricalLowest(candidate: Offer, history: List<PriceSnapshot>): Boolean =
        isHistoricalLowest(
            candidateKey = candidate.comparisonKey,
            candidatePlanType = candidate.planType,
            candidatePrice = candidate.price.actualAmount,
            history = history
        )

    fun isHistoricalLowest(candidate: PriceSnapshot, history: List<PriceSnapshot>): Boolean =
        isHistoricalLowest(
            candidateKey = candidate.comparisonKey,
            candidatePlanType = candidate.planType,
            candidatePrice = candidate.price.actualAmount,
            history = history
        )

    private fun isHistoricalLowest(
        candidateKey: ComparisonKey,
        candidatePlanType: PlanType,
        candidatePrice: Money,
        history: List<PriceSnapshot>
    ): Boolean {
        if (!isEligible(candidateKey, candidatePlanType, candidatePrice)) {
            return false
        }

        val eligibleHistory = history.filter { snapshot ->
            snapshot.comparisonKey.canCompareWith(candidateKey) &&
                isEligible(snapshot.comparisonKey, snapshot.planType, snapshot.price.actualAmount)
        }
        if (eligibleHistory.isEmpty()) {
            return false
        }

        return eligibleHistory.all { snapshot ->
            candidatePrice.amountMinor <= snapshot.price.actualAmount.amountMinor
        }
    }

    private fun isEligible(
        key: ComparisonKey,
        planType: PlanType,
        price: Money
    ): Boolean = planType.isPaid &&
        price.currency == key.currency &&
        price.amountMinor > 0
}
