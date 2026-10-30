package com.aioffer.radar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.network.DemoRemoteDataSource
import com.aioffer.radar.core.network.RemoteDataSource
import com.aioffer.radar.core.network.RemoteSyncPayload
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialUrlEndToEndTest {
    private lateinit var database: RadarDatabase
    private lateinit var repository: LocalRadarRepository

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
    fun officialUrl_flowsFromRemotePayloadToRoomAndRepository() = runBlocking {
        val demoPayload = DemoRemoteDataSource().fetch()
        val firstOffer = demoPayload.offers.first()
        val expectedUrl = "https://www.moonshot.cn/"
        val payload = demoPayload.copy(
            offers = demoPayload.offers.mapIndexed { index, offer ->
                if (index == 0) offer.copy(officialUrl = expectedUrl) else offer
            }
        )
        val syncService = OfferSyncService(
            remoteDataSource = FixedPayloadRemoteDataSource(payload),
            database = database,
            repository = repository,
            clock = Clock.fixed(DemoRemoteDataSource.DEMO_FETCHED_AT, ZoneOffset.UTC)
        )

        val result = syncService.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(
            expectedUrl,
            repository.getOfficialOfferUrl(firstOffer.productId, firstOffer.id)
        )
        val detail = repository.observeProductDetail(firstOffer.productId).first()
        assertNotNull(detail)
        assertEquals(
            expectedUrl,
            detail!!.offers.single { it.id == firstOffer.id }.officialUrl
        )
    }

    private class FixedPayloadRemoteDataSource(
        private val payload: RemoteSyncPayload
    ) : RemoteDataSource {
        override val sourceId: String = payload.source.id

        override suspend fun fetch(): RemoteSyncPayload = payload
    }
}
