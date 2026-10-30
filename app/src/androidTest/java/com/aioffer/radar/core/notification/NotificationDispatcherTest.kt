package com.aioffer.radar.core.notification

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.NotificationEventEntity
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.model.NotificationEvent
import com.aioffer.radar.core.model.NotificationEventStatus
import com.aioffer.radar.core.model.NotificationEventType
import com.aioffer.radar.core.testing.DATABASE_TEST_NOW
import com.aioffer.radar.core.testing.databaseSeedBatch
import com.aioffer.radar.data.LocalRadarRepository
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDispatcherTest {
    private lateinit var database: RadarDatabase
    private lateinit var sender: FakeNotificationSender
    private lateinit var dispatcher: NotificationDispatcher

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
        LocalRadarRepository(database).applySync(databaseSeedBatch())
        database.userSubscriptionDao().subscribe(
            com.aioffer.radar.core.database.UserSubscriptionEntity(
                "kimi",
                DATABASE_TEST_NOW.toEpochMilli()
            )
        )
        sender = FakeNotificationSender()
        dispatcher = NotificationDispatcher(
            database,
            sender,
            Clock.fixed(DATABASE_TEST_NOW, ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun priceDecrease_notifiesOnlySubscribedProductOnce() = runBlocking {
        database.notificationEventDao().insertAllIfAbsent(
            listOf(event("kimi-event", "kimi"), event("deepseek-event", "deepseek"))
        )

        assertEquals(1, dispatcher.dispatchPending())
        dispatcher.dispatchPending()

        assertEquals(listOf("kimi-event"), sender.sentEventIds)
        assertEquals("SENT", database.notificationEventDao().getById("kimi-event")?.status)
        assertEquals("CANCELLED", database.notificationEventDao().getById("deepseek-event")?.status)
    }

    @Test
    fun permissionDenied_keepsEventPendingUntilGranted() = runBlocking {
        database.notificationEventDao().insertIfAbsent(event("kimi-event", "kimi"))
        sender.notificationsEnabled = false

        assertEquals(0, dispatcher.dispatchPending())
        assertEquals(0, sender.sentEventIds.size)
        assertEquals("PENDING", database.notificationEventDao().getById("kimi-event")?.status)

        sender.notificationsEnabled = true
        assertEquals(1, dispatcher.dispatchPending())
        assertEquals(listOf("kimi-event"), sender.sentEventIds)
        assertEquals("SENT", database.notificationEventDao().getById("kimi-event")?.status)
    }

    @Test
    fun deliveredEventWithPendingStatus_onlyRepairsStatus() = runBlocking {
        database.notificationEventDao().insertIfAbsent(event("kimi-event", "kimi"))
        sender.deliveredEventIds += "kimi-event"

        assertEquals(1, dispatcher.dispatchPending())

        assertEquals(0, sender.sentEventIds.size)
        assertEquals("SENT", database.notificationEventDao().getById("kimi-event")?.status)
        assertEquals(
            DATABASE_TEST_NOW.toEpochMilli(),
            database.notificationEventDao().getById("kimi-event")?.sentAtEpochMillis
        )
    }

    private fun event(id: String, productId: String) = NotificationEventEntity(
        eventId = id,
        productId = productId,
        offerId = "demo:offer-1",
        eventType = NotificationEventType.PRICE_DECREASE.name,
        createdAtEpochMillis = DATABASE_TEST_NOW.toEpochMilli(),
        status = NotificationEventStatus.PENDING.name,
        sentAtEpochMillis = null
    )
}

private class FakeNotificationSender : NotificationSender {
    var notificationsEnabled = true
    val deliveredEventIds = mutableSetOf<String>()
    val sentEventIds = mutableListOf<String>()

    override fun canNotify(): Boolean = notificationsEnabled
    override fun wasDelivered(eventId: String): Boolean = eventId in deliveredEventIds
    override fun send(event: NotificationEvent, productName: String) {
        sentEventIds += event.eventId
    }
}
