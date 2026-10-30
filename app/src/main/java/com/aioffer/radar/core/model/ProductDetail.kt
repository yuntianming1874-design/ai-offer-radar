package com.aioffer.radar.core.model

data class ProductDetail(
    val product: Product,
    val plans: List<Plan>,
    val offers: List<Offer>,
    val historicalLowestOfferIds: Set<String>
)
