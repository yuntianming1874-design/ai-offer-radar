package com.aioffer.radar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.NotificationEvent
import com.aioffer.radar.core.model.NotificationEventStatus
import com.aioffer.radar.core.model.NotificationEventType
import com.aioffer.radar.core.model.Region
import com.aioffer.radar.core.testing.DATABASE_TEST_KEY
import com.aioffer.radar.core.testing.DATABASE_TEST_NOW
import com.aioffer.radar.core.testing.DATABASE_TEST_START
import com.aioffer.radar.core.testing.databaseMetadata
import com.aioffer.radar.core.testing.databaseOffer
import com.aioffer.radar.core.testing.databasePlan
import com.aioffer.radar.core.testing.databaseProduct
import com.aioffer.radar.core.testing.databaseSeedBatch
import com.aioffer.radar.core.testing.databaseSnapshot
import com.aioffer.radar.core.testing.databaseSource
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRadarRepositoryTest {
    private lateinit var database: RadarDatabase
    private lateinit var repository: LocalRadarRepository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
        repository = LocalRadarRepository(database)
        repository.applySync(databaseSeedBatch())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun subscribeAndCancel_updatePersistedState() = runBlocking {
        repository.setSubscribed("kimi", true, DATABASE_TEST_NOW)
        assertTrue(repository.isSubscribed("kimi"))

        repository.setSubscribed("kimi", false, DATABASE_TEST_NOW)
        assertFalse(repository.isSubscribed("kimi"))
    }

    @Test
    fun subscribeAll_subscribesEveryProduct() = runBlocking {
        repository.subscribeAll(DATABASE_TEST_NOW)

        assertEquals(
            setOf("kimi", "deepseek", "glm"),
            repository.observeSubscribedProductIds().first().toSet()
        )
    }

    @Test
    fun repeatedOfferSync_doesNotDuplicateOfferOrSnapshot() = runBlocking {
        repository.applySync(databaseSeedBatch())

        assertEquals(1, database.offerDao().count())
        assertEquals(1, database.priceSnapshotDao().getAll().size)
    }

    @Test
    fun currentPriceUpdate_preservesOldSnapshotAndAppendsNewOne() = runBlocking {
        val later = DATABASE_TEST_NOW.plusSeconds(60)
        repository.applySync(
            databaseSeedBatch(
                offers = listOf(databaseOffer(actualAmountMinor = 8_000, updatedAt = later)),
                snapshots = listOf(
                    databaseSnapshot(id = "snapshot-2", actualAmountMinor = 8_000, observedAt = later)
                )
            )
        )

        val snapshots = database.priceSnapshotDao().getAll()
        assertEquals(2, snapshots.size)
        assertEquals(10_000, snapshots[0].actualAmountMinor)
        assertEquals(8_000, snapshots[1].actualAmountMinor)
        assertEquals(8_000, database.offerDao().getAll().single().actualAmountMinor)
    }

    @Test
    fun activeOffers_excludeFutureAndExpiredOffers() = runBlocking {
        repository.applySync(
            databaseSeedBatch(
                offers = listOf(
                    databaseOffer(id = "active"),
                    databaseOffer(
                        id = "future",
                        startsAt = DATABASE_TEST_NOW.plusSeconds(3600)
                    ),
                    databaseOffer(
                        id = "expired",
                        startsAt = DATABASE_TEST_START,
                        endsAt = DATABASE_TEST_START.plusSeconds(3600)
                    )
                )
            )
        )

        val activeIds = repository.getActiveOffers("kimi", DATABASE_TEST_NOW).map { it.id }

        assertEquals(setOf("offer-1", "active"), activeIds.toSet())
        assertFalse(activeIds.contains("future"))
        assertFalse(activeIds.contains("expired"))
    }

    @Test
    fun historyQuery_usesCompleteComparisonKeyAndSortsByTime() = runBlocking {
        repository.applySync(
            databaseSeedBatch(
                snapshots = listOf(
                    databaseSnapshot(id = "later", observedAt = DATABASE_TEST_NOW.plusSeconds(60)),
                    databaseSnapshot(
                        id = "earlier",
                        observedAt = DATABASE_TEST_NOW.minusSeconds(30)
                    ),
                    databaseSnapshot(
                        id = "other-region",
                        key = DATABASE_TEST_KEY.copy(region = Region("HK")),
                        observedAt = DATABASE_TEST_NOW.minusSeconds(60)
                    )
                )
            )
        )

        val history = repository.observePriceHistory(DATABASE_TEST_KEY).first()

        assertEquals(listOf("earlier", "snapshot-1", "later"), history.map { it.id })
    }

    @Test
    fun officialOfferUrl_isReadForMatchingProductAndOffer() = runBlocking {
        assertEquals(
            "https://example.com/subscribe",
            repository.getOfficialOfferUrl("kimi", "offer-1")
        )
        assertEquals(null, repository.getOfficialOfferUrl("deepseek", "offer-1"))
        assertEquals(null, repository.getOfficialOfferUrl("kimi", "missing"))
    }

    @Test
    fun clickStatistics_countByProductAndCanBeCleared() = runBlocking {
        repository.recordOfficialClick("kimi", "offer-1", DATABASE_TEST_NOW)
        repository.recordOfficialClick("kimi", "offer-1", DATABASE_TEST_NOW.plusSeconds(1))
        repository.recordOfficialClick("deepseek", "offer-2", DATABASE_TEST_NOW)

        val statistics = repository.observeClickStatistics().first()
        val counts = statistics.associate { it.productId to it.clickCount }
        assertEquals(2, counts["kimi"])
        assertEquals(1, counts["deepseek"])
        assertEquals("KIMI", statistics.single { it.productId == "kimi" }.productName)

        repository.clearClickHistory()
        assertEquals(0, database.officialClickEventDao().countForProduct("kimi"))
        assertEquals(0, database.officialClickEventDao().countForProduct("deepseek"))
    }

    @Test
    fun notificationEvents_areDeduplicatedByStableEventId() = runBlocking {
        val event = NotificationEvent(
            eventId = "kimi:offer-1:price-drop:8000",
            productId = "kimi",
            offerId = "demo:offer-1",
            eventType = NotificationEventType.PRICE_DECREASE,
            createdAt = DATABASE_TEST_NOW,
            status = NotificationEventStatus.PENDING,
            sentAt = null
        )
        val batch = databaseSeedBatch().copy(notificationEvents = listOf(event))

        repository.applySync(batch)
        repository.applySync(batch)

        assertTrue(repository.hasNotificationEvent(event.eventId))
        assertEquals(1, database.notificationEventDao().count())
    }

    @Test
    fun offerWithMissingSource_isSkippedInsteadOfCrashing() = runBlocking {
        // 模拟 combine 的中间快照：offers 已发出，但对应 source 还没被读到。
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")
        database.offerDao().upsertAll(
            listOf(
                databaseOffer(id = "orphan")
                    .copy(source = databaseSource().copy(id = "missing-source"))
                    .toEntity()
            )
        )
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")

        val kimi = repository.observeHomeProducts(DATABASE_TEST_NOW).first()
            .single { it.product.id == "kimi" }

        assertTrue(kimi.activeOffers.none { it.id == "orphan" })
        assertTrue(kimi.activeOffers.any { it.id == "offer-1" })

        val detail = requireNotNull(repository.observeProductDetail("kimi").first())
        assertTrue(detail.offers.none { it.id == "orphan" })
        assertTrue(detail.offers.any { it.id == "offer-1" })
    }

    @Test
    fun syncMetadata_isUpdatedInsideSync() = runBlocking {
        val metadata = database.syncMetadataDao().getBySourceId("demo")

        assertEquals(true, metadata?.baselineEstablished)
        assertEquals(
            DATABASE_TEST_NOW.toEpochMilli(),
            metadata?.lastSuccessfulSyncAtEpochMillis
        )
    }

    @Test
    fun activeOffers_includeStartBoundaryAndExcludeEndBoundary() = runBlocking {
        repository.applySync(
            databaseSeedBatch(
                offers = listOf(
                    databaseOffer(
                        id = "starts-now",
                        startsAt = DATABASE_TEST_NOW,
                        endsAt = null
                    ),
                    databaseOffer(
                        id = "ends-now",
                        startsAt = DATABASE_TEST_START,
                        endsAt = DATABASE_TEST_NOW
                    ),
                    databaseOffer(
                        id = "starts-later",
                        startsAt = DATABASE_TEST_NOW.plusMillis(1)
                    )
                )
            )
        )

        val activeIds = repository.getActiveOffers("kimi", DATABASE_TEST_NOW).map { it.id }

        assertEquals(setOf("offer-1", "starts-now"), activeIds.toSet())
    }

    @Test
    fun failedApplySync_rollsBackEveryWriteInTheTransaction() = runBlocking {
        val newProduct = databaseProduct("zeta")
        val newPlan = databasePlan("zeta")
        val offerWithMissingPlan = databaseOffer(
            id = "orphan-offer",
            key = DATABASE_TEST_KEY.copy(productId = "zeta", planId = "missing-plan")
        )
        val batch = SyncBatch(
            products = listOf(newProduct),
            plans = listOf(newPlan),
            offers = listOf(offerWithMissingPlan),
            metadata = databaseMetadata()
        )

        assertThrows(Exception::class.java) {
            runBlocking { repository.applySync(batch) }
        }

        assertNull(database.productDao().getById("zeta"))
        assertTrue(database.planDao().getPlansForProduct("zeta").isEmpty())
        assertTrue(database.offerDao().getAll().none { it.id == "demo:orphan-offer" })
        assertEquals(3, database.productDao().count())
        assertEquals(1, database.offerDao().count())
        assertEquals(
            DATABASE_TEST_NOW.toEpochMilli(),
            database.syncMetadataDao().getBySourceId("demo")?.lastSuccessfulSyncAtEpochMillis
        )
    }

    @Test
    fun applyingSync_cannotRelabelAnExistingSourceType() = runBlocking {
        val relabeledSource = databaseSource().copy(
            type = DataSourceType.OFFICIAL_API,
            sourceUrl = "https://example.com/offers"
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.applySync(
                    databaseSeedBatch().copy(sources = listOf(relabeledSource))
                )
            }
        }

        val stored = requireNotNull(database.sourceInfoDao().getById("demo"))
        assertEquals(DataSourceType.DEMO.name, stored.type)
    }
}
