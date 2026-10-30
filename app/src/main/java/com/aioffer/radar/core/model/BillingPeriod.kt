package com.aioffer.radar.core.model

enum class BillingPeriod(val months: Int?) {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12),
    ONE_TIME(null)
}
