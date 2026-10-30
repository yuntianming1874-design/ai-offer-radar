package com.aioffer.radar.core.model

data class Plan(
    val id: String,
    val productId: String,
    val name: String,
    val type: PlanType
)
