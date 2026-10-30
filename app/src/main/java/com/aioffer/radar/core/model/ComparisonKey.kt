package com.aioffer.radar.core.model

data class ComparisonKey(
    val productId: String,
    val planId: String,
    val region: Region,
    val currency: Currency,
    val billingPeriod: BillingPeriod,
    val taxIncluded: Boolean
)
