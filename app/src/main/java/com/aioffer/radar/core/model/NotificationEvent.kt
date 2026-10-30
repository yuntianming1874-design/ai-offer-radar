package com.aioffer.radar.core.model

import java.time.Instant

enum class NotificationEventType {
    NEW_OFFER,
    PRICE_INCREASE,
    PRICE_DECREASE,
    OFFER_ACTIVATED,
    OFFER_EXPIRED
}

enum class NotificationEventStatus {
    PENDING,
    SENT,
    CANCELLED
}

data class NotificationEvent(
    val eventId: String,
    val productId: String,
    val offerId: String?,
    val eventType: NotificationEventType,
    val createdAt: Instant,
    val status: NotificationEventStatus,
    val sentAt: Instant?
)
