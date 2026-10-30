package com.aioffer.radar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.model.NotificationEventType
import com.aioffer.radar.core.network.DemoRemoteDataSource
import com.aioffer.radar.core.network.DemoScenario
import com.aioffer.radar.core.network.InMemoryDemoStateStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfferSyncServiceIntegrationTest {
    private lateinit var database: RadarDatabase
    private lateinit var repository: LocalRadarRepository
    private lateinit var remote: DemoRemoteDataSource
    private lateinit var service: OfferSyncService

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
        repository = LocalRadarRepository(database)
        remote = DemoRemoteDataSource()
        service = OfferSyncService(
            remoteDataSource = remote,
            database = database,
            repository = repository,
            clock = Clock.fixed(DemoRemoteDataSource.DEMO_FETCHED_AT, ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstSync_buildsBaselineWithoutNotifications() = runBlocking {
        val result = service.sync()

        assertTrue(result is SyncResult.Success)
        result as SyncResult.Success
        assertTrue(result.baselineCreated)
        assertEquals(0, result.notificationEventsCreated)
        assertEquals(3, database.productDao().count())
        assertEquals(3, database.offerDao().count())
        assertEquals(3, database.priceSnapshotDao().getAll().size)
        assertEquals(0, database.notificationEventDao().count())
    }

    @Test
    fun firstSync_hasNoHistoricalLowestBadge() = runBlocking {
        service.sync()

        val home = repository.observeHomeProducts(DemoRemoteDataSource.DEMO_FETCHED_AT).first()

        assertTrue(home.all { it.historicalLowestOfferIds.isEmpty() })
    }

    @Test
    fun priceDecrease_marksOnlyItsComparisonGroupAsHistoricalLowest() = runBlocking {
        service.sync()
        remote.setNextScenario(DemoScenario.PRICE_DECREASE)
        syncAt(DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(60))

        val home = repository.observeHomeProducts(DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(120)).first()
        val kimi = home.single { it.product.id == "demo-kimi" }

        assertEquals(
            setOf(DemoRemoteDataSource.KIMI_WELCOME_OFFER_ID),
            kimi.historicalLowestOfferIds
        )
        assertTrue(
            home.filterNot { it.product.id == "demo-kimi" }
                .all { it.historicalLowestOfferIds.isEmpty() }
        )
    }

    @Test
    fun newOffer_marksOnlyTheCheaperOfferAsHistoricalLowest() = runBlocking {
        service.sync()
        remote.setNextScenario(DemoScenario.NEW_OFFER)
        syncAt(DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(60))

        val home = repository.observeHomeProducts(
            DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(120)
        ).first()
        val kimi = home.single { it.product.id == "demo-kimi" }

        assertEquals(
            setOf(DemoRemoteDataSource.NEW_OFFER_ID),
            kimi.historicalLowestOfferIds
        )
    }

    @Test
    fun newOffer_notifiesSubscribedProductOnlyOnce() = runBlocking {
        service.sync()
        repository.setSubscribed("demo-kimi", true, DemoRemoteDataSource.DEMO_FETCHED_AT)
        remote.setNextScenario(DemoScenario.NEW_OFFER)
        service.sync()
        remote.setNextScenario(DemoScenario.REPEAT)
        service.sync()

        val events = database.notificationEventDao().getAll()
        assertEquals(1, events.size)
        assertEquals(NotificationEventType.NEW_OFFER.name, events.single().eventType)
        assertEquals(4, database.offerDao().count())
        assertEquals(4, database.priceSnapshotDao().getAll().size)
    }

    @Test
    fun priceDecrease_notifiesOnlyOnce() = runBlocking {
        service.sync()
        repository.setSubscribed("demo-kimi", true, DemoRemoteDataSource.DEMO_FETCHED_AT)
        remote.setNextScenario(DemoScenario.PRICE_DECREASE)
        service.sync()
        remote.setNextScenario(DemoScenario.REPEAT)
        service.sync()

        val events = database.notificationEventDao().getAll()
        assertEquals(1, events.size)
        assertEquals(NotificationEventType.PRICE_DECREASE.name, events.single().eventType)
        assertEquals(4, database.priceSnapshotDao().getAll().size)
    }

    @Test
    fun priceIncrease_notifiesOnlyOnce() = runBlocking {
        service.sync()
        repository.setSubscribed("demo-kimi", true, DemoRemoteDataSource.DEMO_FETCHED_AT)
        remote.setNextScenario(DemoScenario.PRICE_INCREASE)
        service.sync()
        remote.setNextScenario(DemoScenario.REPEAT)
        service.sync()

        val events = database.notificationEventDao().getAll()
        assertEquals(1, events.size)
        assertEquals(NotificationEventType.PRICE_INCREASE.name, events.single().eventType)
    }

    @Test
    fun returningToEarlierAmount_appendsNewSnapshot() = runBlocking {
        service.sync()
        repository.setSubscribed("demo-kimi", true, DemoRemoteDataSource.DEMO_FETCHED_AT)

        remote.setNextScenario(DemoScenario.PRICE_DECREASE)
        syncAt(DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(60))
        remote.setNextScenario(DemoScenario.PRICE_INCREASE)
        syncAt(DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(120))
        remote.setNextScenario(DemoScenario.PRICE_DECREASE)
        syncAt(DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(180))

        val kimiSnapshots = database.priceSnapshotDao().getAll()
            .filter { it.productId == "demo-kimi" }
        assertEquals(listOf(10_000L, 8_000L, 11_000L, 8_000L), kimiSnapshots.map { it.actualAmountMinor })
        assertEquals(3, database.notificationEventDao().count())
    }

    @Test
    fun unsubscribedProduct_doesNotNotify() = runBlocking {
        service.sync()
        remote.setNextScenario(DemoScenario.PRICE_DECREASE)
        service.sync()

        assertEquals(0, database.notificationEventDao().count())
        assertEquals(4, database.priceSnapshotDao().getAll().size)
    }

    @Test
    fun repeatedSync_doesNotDuplicateOffersSnapshotsOrNotifications() = runBlocking {
        service.sync()
        repository.setSubscribed("demo-kimi", true, DemoRemoteDataSource.DEMO_FETCHED_AT)
        remote.setNextScenario(DemoScenario.REPEAT)
        val result = service.sync()

        result as SyncResult.Success
        assertEquals(0, result.snapshotsAppended)
        assertEquals(0, result.notificationEventsCreated)
        assertEquals(3, database.offerDao().count())
        assertEquals(3, database.priceSnapshotDao().getAll().size)
        assertEquals(0, database.notificationEventDao().count())
    }

    @Test
    fun networkFailure_preservesBusinessDataAndSuccessfulBaseline() = runBlocking {
        service.sync()
        remote.setNextScenario(DemoScenario.NETWORK_FAILURE)
        val result = service.sync()

        assertTrue(result is SyncResult.Failure)
        assertEquals(3, database.offerDao().count())
        assertEquals(3, database.priceSnapshotDao().getAll().size)
        assertEquals(0, database.notificationEventDao().count())
        val metadata = database.syncMetadataDao().getBySourceId(remote.sourceId)
        assertEquals("FAILED", metadata?.status)
        assertEquals(true, metadata?.baselineEstablished)
        assertEquals(
            DemoRemoteDataSource.DEMO_FETCHED_AT.toEpochMilli(),
            metadata?.lastSuccessfulSyncAtEpochMillis
        )
    }

    @Test
    fun demoStateSurvivesProcessRestart_withoutNewEventsOrSnapshots() = runBlocking {
        val stateStore = InMemoryDemoStateStore()
        remote = DemoRemoteDataSource(stateStore)
        service = OfferSyncService(
            remoteDataSource = remote,
            database = database,
            repository = repository,
            clock = Clock.fixed(DemoRemoteDataSource.DEMO_FETCHED_AT, ZoneOffset.UTC)
        )

        service.sync()
        repository.setSubscribed("demo-kimi", true, DemoRemoteDataSource.DEMO_FETCHED_AT)
        remote.setNextScenario(DemoScenario.PRICE_DECREASE)
        service.sync()

        assertEquals(1, database.notificationEventDao().count())
        assertEquals(4, database.priceSnapshotDao().getAll().size)

        val restartedRemote = DemoRemoteDataSource(stateStore)
        val restartedService = OfferSyncService(
            remoteDataSource = restartedRemote,
            database = database,
            repository = repository,
            clock = Clock.fixed(DemoRemoteDataSource.DEMO_FETCHED_AT, ZoneOffset.UTC)
        )

        val result = restartedService.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(1, database.notificationEventDao().count())
        assertEquals(4, database.priceSnapshotDao().getAll().size)
        assertEquals(3, database.offerDao().count())
    }

    @Test
    fun failedFirstSync_doesNotEstablishBaseline() = runBlocking {
        remote.setNextScenario(DemoScenario.NETWORK_FAILURE)
        service.sync()
        val failedMetadata = database.syncMetadataDao().getBySourceId(remote.sourceId)
        assertFalse(failedMetadata?.baselineEstablished ?: true)

        remote.setNextScenario(DemoScenario.INITIAL_SYNC)
        service.sync()

        assertEquals(0, database.notificationEventDao().count())
        assertEquals(true, database.syncMetadataDao().getBySourceId(remote.sourceId)?.baselineEstablished)
    }

    private suspend fun syncAt(instant: Instant) {
        OfferSyncService(
            remoteDataSource = remote,
            database = database,
            repository = repository,
            clock = Clock.fixed(instant, ZoneOffset.UTC)
        ).sync()
    }
}
