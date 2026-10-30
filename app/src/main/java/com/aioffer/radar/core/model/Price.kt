package com.aioffer.radar.core.model

data class Price(
    val originalAmount: Money,
    val actualAmount: Money,
    val monthlyEquivalent: Money? = null
)
