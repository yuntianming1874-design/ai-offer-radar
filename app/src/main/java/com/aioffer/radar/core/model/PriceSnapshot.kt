package com.aioffer.radar.core.model

import java.time.Instant

data class PriceSnapshot(
    val id: String,
    val comparisonKey: ComparisonKey,
    val planType: PlanType,
    val price: Price,
    val observedAt: Instant
)
