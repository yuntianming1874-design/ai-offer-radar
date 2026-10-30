package com.aioffer.radar.data

import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.network.DemoRemoteDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePayloadNormalizerTest {
    @Test
    fun validDemoPayload_normalizesIntoDomainModels() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()

        val normalized = RemotePayloadNormalizer.normalize(payload)

        assertEquals(DataSourceType.DEMO, normalized.source.type)
        assertEquals(payload.products.size, normalized.products.size)
        assertEquals(payload.plans.size, normalized.plans.size)
        assertEquals(payload.offers.size, normalized.offers.size)
    }

    @Test
    fun officialUrl_isPreservedFromRemoteOffer(): Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val expectedUrl = "https://www.moonshot.cn/"
        val modified = payload.copy(
            offers = payload.offers.mapIndexed { index, offer ->
                if (index == 0) offer.copy(officialUrl = expectedUrl) else offer
            }
        )

        val normalized = RemotePayloadNormalizer.normalize(modified)

        assertEquals(expectedUrl, normalized.offers.first().officialUrl)
    }

    @Test
    fun negativeAmount_isRejected() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val invalid = payload.copy(
            offers = payload.offers.mapIndexed { index, offer ->
                if (index == 0) offer.copy(actualAmountMinor = -1) else offer
            }
        )

        assertThrows(RemotePayloadNormalizationException::class.java) {
            RemotePayloadNormalizer.normalize(invalid)
        }
    }

    @Test
    fun officialSourceWithoutUrl_isRejected() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val invalid = payload.copy(
            source = payload.source.copy(sourceType = "OFFICIAL_API", sourceUrl = null)
        )

        assertThrows(RemotePayloadNormalizationException::class.java) {
            RemotePayloadNormalizer.normalize(invalid)
        }
    }

    @Test
    fun sourceWithoutFetchTime_isRejected() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val invalid = payload.copy(
            source = payload.source.copy(fetchedAtEpochMillis = 0)
        )

        val error = assertThrows(RemotePayloadNormalizationException::class.java) {
            RemotePayloadNormalizer.normalize(invalid)
        }
        assertTrue(error.message.orEmpty().contains("fetch time"))
    }

    @Test
    fun officialSourceUsingDemoId_isRejected() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val invalid = payload.copy(
            source = payload.source.copy(
                id = "demo-official",
                sourceType = "OFFICIAL_API",
                sourceUrl = "https://example.com/offers"
            )
        )

        val error = assertThrows(RemotePayloadNormalizationException::class.java) {
            RemotePayloadNormalizer.normalize(invalid)
        }
        assertTrue(error.message.orEmpty().contains("Source IDs"))
    }

    @Test
    fun officialPayloadUsingDemoProductId_isRejected() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val invalid = payload.copy(
            source = payload.source.copy(
                id = "official-test",
                sourceType = "OFFICIAL_API",
                sourceUrl = "https://example.com/offers"
            ),
            products = payload.products.map { it.copy(isDemo = false) }
        )

        val error = assertThrows(RemotePayloadNormalizationException::class.java) {
            RemotePayloadNormalizer.normalize(invalid)
        }
        assertTrue(error.message.orEmpty().contains("Product IDs"))
    }

    @Test
    fun demoPayloadUsingOfficialProductId_isRejected() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val invalid = payload.copy(
            products = payload.products.mapIndexed { index, product ->
                if (index == 0) product.copy(id = "kimi-official") else product
            }
        )

        val error = assertThrows(RemotePayloadNormalizationException::class.java) {
            RemotePayloadNormalizer.normalize(invalid)
        }
        assertTrue(error.message.orEmpty().contains("Product IDs"))
    }

    @Test
    fun offerWithoutOriginalTitle_isRejected() : Unit = runBlocking {
        val payload = DemoRemoteDataSource().fetch()
        val invalid = payload.copy(
            offers = payload.offers.mapIndexed { index, offer ->
                if (index == 0) offer.copy(title = "   ") else offer
            }
        )

        val error = assertThrows(RemotePayloadNormalizationException::class.java) {
            RemotePayloadNormalizer.normalize(invalid)
        }
        assertTrue(error.message.orEmpty().contains("title"))
    }
}
