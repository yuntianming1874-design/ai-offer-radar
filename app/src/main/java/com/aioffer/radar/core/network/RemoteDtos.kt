package com.aioffer.radar.core.network

data class RemoteProductDto(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val isDemo: Boolean
)

data class RemotePlanDto(
    val id: String,
    val productId: String,
    val name: String,
    val planType: String
)

data class RemoteSourceDto(
    val id: String,
    val sourceType: String,
    val sourceUrl: String?,
    val titleOrSummary: String,
    val fetchedAtEpochMillis: Long
)

data class RemoteOfferDto(
    val id: String,
    val productId: String,
    val planId: String,
    val planType: String,
    val offerType: String,
    val title: String,
    val discountDescription: String,
    val regionCode: String,
    val currencyCode: String,
    val billingPeriod: String,
    val taxIncluded: Boolean,
    val originalAmountMinor: Long,
    val actualAmountMinor: Long,
    val monthlyEquivalentMinor: Long?,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
    val source: RemoteSourceDto,
    val officialUrl: String? = null
)

data class RemoteSyncPayload(
    val source: RemoteSourceDto,
    val products: List<RemoteProductDto>,
    val plans: List<RemotePlanDto>,
    val offers: List<RemoteOfferDto>
)
