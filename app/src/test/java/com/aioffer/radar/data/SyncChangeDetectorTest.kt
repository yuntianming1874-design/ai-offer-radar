package com.aioffer.radar.data

import com.aioffer.radar.core.model.NotificationEventType
import com.aioffer.radar.core.testing.BASE_COMPARISON_KEY
import com.aioffer.radar.core.testing.testOffer
import com.aioffer.radar.core.testing.testPrice
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncChangeDetectorTest {
    private val now = Instant.parse("2026-06-01T00:00:00Z")
    private val subscribed = setOf("kimi")

    @Test
    fun firstSync_createsSnapshotsWithoutNotifications() {
        val remote = listOf(testOffer())

        val changes = SyncChangeDetector.detect(emptyList(), remote, false, subscribed, now)

        assertEquals(1, changes.snapshotsToAppend.size)
        assertTrue(changes.notificationEvents.isEmpty())
    }

    @Test
    fun newOffer_isEmittedOnlyForItsFirstChange() {
        val existing = listOf(testOffer(id = "existing"))
        val newOffer = testOffer(id = "new")
        val remote = existing + newOffer

        val first = SyncChangeDetector.detect(existing, remote, true, subscribed, now)
        val repeated = SyncChangeDetector.detect(remote, remote, true, subscribed, now)

        assertEquals(listOf(NotificationEventType.NEW_OFFER), first.notificationEvents.map { it.eventType })
        assertEquals(1, first.snapshotsToAppend.size)
        assertTrue(repeated.notificationEvents.isEmpty())
        assertTrue(repeated.snapshotsToAppend.isEmpty())
    }

    @Test
    fun priceDecrease_emitsOneDecreaseEvent() {
        val previous = listOf(testOffer(price = testPrice(10_000)))
        val current = listOf(testOffer(price = testPrice(8_000)))

        val changes = SyncChangeDetector.detect(previous, current, true, subscribed, now)

        assertEquals(
            listOf(NotificationEventType.PRICE_DECREASE),
            changes.notificationEvents.map { it.eventType }
        )
    }

    @Test
    fun priceIncrease_emitsOneIncreaseEvent() {
        val previous = listOf(testOffer(price = testPrice(10_000)))
        val current = listOf(testOffer(price = testPrice(12_000)))

        val changes = SyncChangeDetector.detect(previous, current, true, subscribed, now)

        assertEquals(
            listOf(NotificationEventType.PRICE_INCREASE),
            changes.notificationEvents.map { it.eventType }
        )
    }

    @Test
    fun unsubscribedProduct_changeDoesNotEmitNotification() {
        val previous = listOf(testOffer(price = testPrice(10_000)))
        val current = listOf(testOffer(price = testPrice(8_000)))

        val changes = SyncChangeDetector.detect(previous, current, true, emptySet(), now)

        assertTrue(changes.notificationEvents.isEmpty())
        assertEquals(1, changes.snapshotsToAppend.size)
    }

    @Test
    fun differentComparisonKey_priceChangeIsNotCompared() {
        val previous = listOf(testOffer(price = testPrice(10_000)))
        val current = listOf(
            testOffer(
                key = BASE_COMPARISON_KEY.copy(planId = "kimi-student"),
                price = testPrice(8_000)
            )
        )

        val changes = SyncChangeDetector.detect(previous, current, true, subscribed, now)

        assertTrue(changes.notificationEvents.isEmpty())
        assertEquals(1, changes.snapshotsToAppend.size)
    }

    @Test
    fun futureOffer_becomingActiveEmitsActivation() {
        val previous = listOf(testOffer(startsAt = now.plusSeconds(3600)))
        val current = listOf(testOffer(startsAt = now.minusSeconds(1)))

        val changes = SyncChangeDetector.detect(previous, current, true, subscribed, now)

        assertEquals(
            listOf(NotificationEventType.OFFER_ACTIVATED),
            changes.notificationEvents.map { it.eventType }
        )
    }

    @Test
    fun activeOffer_expiringEmitsExpiration() {
        val previous = listOf(testOffer())
        val current = listOf(testOffer(endsAt = now))

        val changes = SyncChangeDetector.detect(previous, current, true, subscribed, now)

        assertEquals(
            listOf(NotificationEventType.OFFER_EXPIRED),
            changes.notificationEvents.map { it.eventType }
        )
    }

    @Test
    fun returningToEarlierAmount_atLaterObservation_keepsBothSnapshots() {
        val initial = listOf(testOffer(price = testPrice(10_000)))
        val dropped = SyncChangeDetector.detect(
            initial,
            listOf(testOffer(price = testPrice(8_000))),
            true,
            subscribed,
            now
        )
        val increased = SyncChangeDetector.detect(
            dropped.offersToPersist,
            listOf(testOffer(price = testPrice(11_000))),
            true,
            subscribed,
            now.plusSeconds(60)
        )
        val droppedAgain = SyncChangeDetector.detect(
            increased.offersToPersist,
            listOf(testOffer(price = testPrice(8_000))),
            true,
            subscribed,
            now.plusSeconds(120)
        )

        assertEquals(1, droppedAgain.snapshotsToAppend.size)
        assertNotEquals(
            dropped.snapshotsToAppend.single().id,
            droppedAgain.snapshotsToAppend.single().id
        )
    }

    @Test
    fun eventIds_areStableForTheSameBusinessChange() {
        val previous = listOf(testOffer(price = testPrice(10_000)))
        val current = listOf(testOffer(price = testPrice(8_000)))

        val first = SyncChangeDetector.detect(previous, current, true, subscribed, now)
        val second = SyncChangeDetector.detect(previous, current, true, subscribed, now)

        assertEquals(first.notificationEvents.single().eventId, second.notificationEvents.single().eventId)
    }
}
