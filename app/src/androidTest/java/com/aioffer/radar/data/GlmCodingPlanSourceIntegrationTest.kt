package com.aioffer.radar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.network.GLM_CODING_PLAN_SOURCE_ID
import com.aioffer.radar.core.network.GlmCodingPlanRemoteDataSource
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
class GlmCodingPlanSourceIntegrationTest {
    private lateinit var database: RadarDatabase
    private lateinit var repository: LocalRadarRepository

    private val fetchedAt: Instant = Instant.ofEpochMilli(1_793_374_402_239L)

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
    fun collectorPayload_persistsNinePeriodPricedOffers() = runBlocking {
        val result = syncService().sync()
        assertTrue(result is SyncResult.Success)

        val storedSource = requireNotNull(database.sourceInfoDao().getById(GLM_CODING_PLAN_SOURCE_ID))
        assertEquals(DataSourceType.OFFICIAL_WEB.name, storedSource.type)
        assertEquals("https://www.bigmodel.cn/glm-coding", storedSource.sourceUrl)
        assertEquals(fetchedAt.toEpochMilli(), storedSource.fetchedAtEpochMillis)

        val offers = database.offerDao().getBySourceId(GLM_CODING_PLAN_SOURCE_ID)
        assertEquals(9, offers.size)
        val liteQuarterly = offers.single {
            it.remoteId == "glm-coding-plan-official-lite-quarterly"
        }
        assertEquals("QUARTERLY", liteQuarterly.billingPeriod)
        assertEquals(28_320L, liteQuarterly.actualAmountMinor)
        assertEquals(9_440L, liteQuarterly.monthlyEquivalentMinor)
        assertEquals(35_400L, liteQuarterly.originalAmountMinor)
        assertEquals(true, liteQuarterly.taxIncluded)
        assertEquals(
            "https://www.bigmodel.cn/glm-coding",
            liteQuarterly.officialUrl
        )

        val home = repository.observeHomeProducts(fetchedAt.plusSeconds(1)).first()
        assertEquals(3, home.size)
        home.forEach { product ->
            assertFalse(product.product.isDemo)
            assertEquals(3, product.activeOffers.size)
            assertEquals(
                setOf(BillingPeriod.MONTHLY, BillingPeriod.QUARTERLY, BillingPeriod.YEARLY),
                product.activeOffers.map { it.comparisonKey.billingPeriod }.toSet()
            )
            assertTrue(
                product.activeOffers.all {
                    it.source.type == DataSourceType.OFFICIAL_WEB &&
                        it.source.sourceUrl == "https://www.bigmodel.cn/glm-coding"
                }
            )
        }
    }

    private fun syncService(): OfferSyncService {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val payload = requireNotNull(testContext.assets.open("glm-coding-plan-payload.json").use {
            it.bufferedReader().readText()
        })
        return OfferSyncService(
            remoteDataSource = GlmCodingPlanRemoteDataSource(httpGet = { payload }),
            database = database,
            repository = repository,
            clock = Clock.fixed(fetchedAt, ZoneOffset.UTC)
        )
    }
}
