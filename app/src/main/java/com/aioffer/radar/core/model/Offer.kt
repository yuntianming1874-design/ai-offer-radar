package com.aioffer.radar.core.model

import java.time.Instant

data class Offer(
    val id: String,
    val comparisonKey: ComparisonKey,
    val planType: PlanType,
    val offerType: OfferType,
    val title: String,
    val discountDescription: String,
    val price: Price,
    val startsAt: Instant,
    val endsAt: Instant?,
    val source: SourceInfo,
    val updatedAt: Instant,
    val officialUrl: String? = null
) {
    init {
        require(endsAt == null || endsAt.isAfter(startsAt)) {
            "Offer end time must be after its start time"
        }
    }
}
