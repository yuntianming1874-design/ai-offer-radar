package com.aioffer.radar.core.model

data class Money(
    val amountMinor: Long,
    val currency: Currency
) {
    val isNegative: Boolean
        get() = amountMinor < 0
}
