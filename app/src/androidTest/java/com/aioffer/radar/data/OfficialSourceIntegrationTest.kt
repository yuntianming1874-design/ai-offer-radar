package com.aioffer.radar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.network.DemoRemoteDataSource
import com.aioffer.radar.core.network.RemoteDataSource
import com.aioffer.radar.core.network.RemoteOfferDto
import com.aioffer.radar.core.network.RemotePlanDto
import com.aioffer.radar.core.network.RemoteProductDto
import com.aioffer.radar.core.network.RemoteSourceDto
import com.aioffer.radar.core.network.RemoteSyncPayload
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
class OfficialSourceIntegrationTest {
    private lateinit var database: RadarDatabase
    private lateinit var repository: LocalRadarRepository

    private val fetchedAt: Instant = Instant.parse("2026-06-01T00:00:00Z")
    private val source = RemoteSourceDto(
        id = "official-test",
        sourceType = "OFFICIAL_API",
        sourceUrl = "https://offers.example.com/kimi",
        titleOrSummary = "Kimi official pricing page",
        fetchedAtEpochMillis = fetchedAt.toEpochMilli()
    )
    private val officialPayload = RemoteSyncPayload(
        source = source,
        products = listOf(
            RemoteProductDto(
                id = "kimi-official",
                name = "KIMI Official",
                logoUrl = null,
                isDemo = false
            )
        ),
        plans = listOf(
            RemotePlanDto(
                id = "kimi-official-standard",
                productId = "kimi-official",
                name = "Standard",
                planType = "STANDARD"
            )
        ),
        offers = listOf(
            RemoteOfferDto(
                id = "official-offer-1",
                productId = "kimi-official",
                planId = "kimi-official-standard",
                planType = "STANDARD",
                offerType = "PROMOTION",
                title = "Official monthly offer",
                discountDescription = "Official source snapshot",
                regionCode = "CN",
                currencyCode = "CNY",
                billingPeriod = "MONTHLY",
                taxIncluded = true,
                originalAmountMinor = 12_000,
                actualAmountMinor = 9_000,
                monthlyEquivalentMinor = 9_000,
                startsAtEpochMillis = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(),
                endsAtEpochMillis = null,
                updatedAtEpochMillis = fetchedAt.toEpochMilli(),
                source = source,
                officialUrl = "https://offers.example.com/kimi/subscribe"
            )
        )
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
        repository = LocalRadarRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun officialFakeSource_persistsTrustMetadataIntoRoom() = runBlocking {
        val result = officialSyncService().sync()

        assertTrue(result is SyncResult.Success)

        val storedSource = requireNotNull(database.sourceInfoDao().getById("official-test"))
        assertEquals(DataSourceType.OFFICIAL_API.name, storedSource.type)
        assertEquals("https://offers.example.com/kimi", storedSource.sourceUrl)
        assertEquals(fetchedAt.toEpochMilli(), storedSource.fetchedAtEpochMillis)

        val home = repository.observeHomeProducts(fetchedAt.plusSeconds(1)).first()
        val official = home.single { it.product.id == "kimi-official" }
        assertFalse(official.product.isDemo)
        assertEquals(DataSourceType.OFFICIAL_API, official.activeOffers.single().source.type)
        assertEquals(
            "https://offers.example.com/kimi/subscribe",
            official.activeOffers.single().officialUrl
        )
        assertEquals(
            "https://offers.example.com/kimi/subscribe",
            repository.getOfficialOfferUrl("kimi-official", "official-offer-1")
        )
    }

    @Test
    fun demoAndOfficialSources_coexistWithoutChangingEachOtherTypes() = runBlocking {
        val demoResult = OfferSyncService(
            remoteDataSource = DemoRemoteDataSource(),
            database = database,
            repository = repository,
            clock = Clock.fixed(DemoRemoteDataSource.DEMO_FETCHED_AT, ZoneOffset.UTC)
        ).sync()
        assertTrue(demoResult is SyncResult.Success)

        val officialResult = officialSyncService().sync()
        assertTrue(officialResult is SyncResult.Success)

        val sources = database.sourceInfoDao().observeSources().first().associateBy { it.id }
        assertEquals(DataSourceType.DEMO.name, sources.getValue("demo-local").type)
        assertEquals(DataSourceType.OFFICIAL_API.name, sources.getValue("official-test").type)

        val home = repository.observeHomeProducts(fetchedAt.plusSeconds(1)).first()
        val demoProducts = home.filter { it.product.isDemo }
        assertEquals(3, demoProducts.size)
        assertTrue(
            demoProducts.all { product ->
                product.activeOffers.isNotEmpty() &&
                    product.activeOffers.all { it.source.type == DataSourceType.DEMO }
            }
        )

        val official = home.single { it.product.id == "kimi-official" }
        assertFalse(official.product.isDemo)
        assertTrue(official.activeOffers.all { it.source.type == DataSourceType.OFFICIAL_API })
    }

    private fun officialSyncService() = OfferSyncService(
        remoteDataSource = FixedPayloadRemoteDataSource(officialPayload),
        database = database,
        repository = repository,
        clock = Clock.fixed(fetchedAt, ZoneOffset.UTC)
    )

    private class FixedPayloadRemoteDataSource(
        private val payload: RemoteSyncPayload
    ) : RemoteDataSource {
        override val sourceId: String = payload.source.id

        override suspend fun fetch(): RemoteSyncPayload = payload
    }
}
