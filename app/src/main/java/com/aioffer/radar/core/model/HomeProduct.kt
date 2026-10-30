package com.aioffer.radar.core.model

data class HomeProduct(
    val product: Product,
    val activeOffers: List<Offer>,
    val isSubscribed: Boolean,
    val historicalLowestOfferIds: Set<String> = emptySet()
)
