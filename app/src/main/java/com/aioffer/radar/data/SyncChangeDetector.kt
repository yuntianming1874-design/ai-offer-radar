package com.aioffer.radar.data

import com.aioffer.radar.core.model.NotificationEvent
import com.aioffer.radar.core.model.NotificationEventStatus
import com.aioffer.radar.core.model.NotificationEventType
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.domain.isActiveAt
import java.time.Instant

data class SyncChanges(
    val offersToPersist: List<Offer>,
    val snapshotsToAppend: List<PriceSnapshot>,
    val notificationEvents: List<NotificationEvent>
)

object SyncChangeDetector {
    fun detect(
        existingOffers: List<Offer>,
        remoteOffers: List<Offer>,
        baselineEstablished: Boolean,
        subscribedProductIds: Set<String>,
        now: Instant
    ): SyncChanges {
        val existingById = existingOffers.associateBy { stableOfferId(it.source.id, it.id) }
        val remoteIds = remoteOffers.mapTo(mutableSetOf()) { stableOfferId(it.source.id, it.id) }
        val snapshots = mutableListOf<PriceSnapshot>()
        val events = mutableListOf<NotificationEvent>()

        remoteOffers.forEach { current ->
            val stableId = stableOfferId(current.source.id, current.id)
            val previous = existingById[stableId]
            if (previous == null ||
                previous.comparisonKey != current.comparisonKey ||
                previous.price != current.price
            ) {
                snapshots += current.toSnapshot(now)
            }

            val eventType = when {
                previous == null && current.isActiveAt(now) -> NotificationEventType.NEW_OFFER
                previous == null -> null
                !previous.isActiveAt(now) && current.isActiveAt(now) ->
                    NotificationEventType.OFFER_ACTIVATED
                previous.isActiveAt(now) && !current.isActiveAt(now) ->
                    NotificationEventType.OFFER_EXPIRED
                previous.isActiveAt(now) &&
                    current.isActiveAt(now) &&
                    previous.comparisonKey == current.comparisonKey &&
                    previous.price.actualAmount.amountMinor != current.price.actualAmount.amountMinor -> {
                    if (current.price.actualAmount.amountMinor < previous.price.actualAmount.amountMinor) {
                        NotificationEventType.PRICE_DECREASE
                    } else {
                        NotificationEventType.PRICE_INCREASE
                    }
                }
                else -> null
            }
            if (eventType != null) {
                events += createEvent(eventType, current, previous, now)
            }
        }

        val expiredMissingOffers = existingOffers
            .filter { stableOfferId(it.source.id, it.id) !in remoteIds && it.isActiveAt(now) }
            .map { previous ->
                val expired = previous.copy(endsAt = now, updatedAt = now)
                events += createEvent(NotificationEventType.OFFER_EXPIRED, expired, previous, now)
                expired
            }

        val subscribedEvents = if (baselineEstablished) {
            events.filter { it.productId in subscribedProductIds }
        } else {
            emptyList()
        }
        return SyncChanges(
            offersToPersist = remoteOffers + expiredMissingOffers,
            snapshotsToAppend = snapshots,
            notificationEvents = subscribedEvents
        )
    }

    private fun Offer.toSnapshot(now: Instant) = PriceSnapshot(
        id = listOf(
            stableOfferId(source.id, id),
            comparisonKey.productId,
            comparisonKey.planId,
            comparisonKey.region.code,
            comparisonKey.currency.name,
            comparisonKey.billingPeriod.name,
            comparisonKey.taxIncluded.toString(),
            updatedAt.toEpochMilli().toString(),
            now.toEpochMilli().toString(),
            price.actualAmount.amountMinor.toString()
        ).joinToString(":"),
        comparisonKey = comparisonKey,
        planType = planType,
        price = price,
        observedAt = now
    )

    private fun createEvent(
        type: NotificationEventType,
        current: Offer,
        previous: Offer?,
        now: Instant
    ): NotificationEvent {
        val stableId = stableOfferId(current.source.id, current.id)
        val eventId = listOf(
            type.name,
            stableId,
            current.comparisonKey.productId,
            current.comparisonKey.planId,
            current.comparisonKey.region.code,
            current.comparisonKey.currency.name,
            current.comparisonKey.billingPeriod.name,
            current.comparisonKey.taxIncluded.toString(),
            current.updatedAt.toEpochMilli().toString(),
            previous?.price?.actualAmount?.amountMinor?.toString() ?: "none",
            current.price.actualAmount.amountMinor.toString()
        ).joinToString(":")
        return NotificationEvent(
            eventId = eventId,
            productId = current.comparisonKey.productId,
            offerId = stableId,
            eventType = type,
            createdAt = now,
            status = NotificationEventStatus.PENDING,
            sentAt = null
        )
    }
}
