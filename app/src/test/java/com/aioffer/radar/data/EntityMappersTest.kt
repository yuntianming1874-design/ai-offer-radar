package com.aioffer.radar.data

import com.aioffer.radar.core.testing.testOffer
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappersTest {
    @Test
    fun officialUrl_survivesOfferEntityRoundTrip() {
        val officialUrl = "https://www.moonshot.cn/"
        val offer = testOffer().copy(officialUrl = officialUrl)

        val entity = offer.toEntity()

        assertEquals(officialUrl, entity.officialUrl)
        assertEquals(officialUrl, entity.toDomain(offer.source.toEntity()).officialUrl)
    }
}
