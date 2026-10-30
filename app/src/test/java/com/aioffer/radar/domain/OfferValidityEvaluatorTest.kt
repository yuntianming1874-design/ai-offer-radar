package com.aioffer.radar.domain

import com.aioffer.radar.core.testing.testOffer
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferValidityEvaluatorTest {
    private val startsAt = Instant.parse("2026-01-01T00:00:00Z")
    private val endsAt = Instant.parse("2026-02-01T00:00:00Z")

    @Test
    fun beforeStart_isNotYetEffective() {
        val offer = testOffer(startsAt = startsAt, endsAt = endsAt)

        val result = OfferValidityEvaluator.evaluate(offer, startsAt.minusMillis(1))

        assertEquals(OfferValidity.NOT_YET_EFFECTIVE, result)
        assertFalse(offer.isActiveAt(startsAt.minusMillis(1)))
    }

    @Test
    fun atStart_isActive() {
        val offer = testOffer(startsAt = startsAt, endsAt = endsAt)

        assertEquals(OfferValidity.ACTIVE, OfferValidityEvaluator.evaluate(offer, startsAt))
        assertTrue(offer.isActiveAt(startsAt))
    }

    @Test
    fun withoutEnd_isActiveAfterStart() {
        val offer = testOffer(startsAt = startsAt, endsAt = null)

        val result = OfferValidityEvaluator.evaluate(offer, Instant.parse("2036-01-01T00:00:00Z"))

        assertEquals(OfferValidity.ACTIVE, result)
    }

    @Test
    fun atEnd_isExpired() {
        val offer = testOffer(startsAt = startsAt, endsAt = endsAt)

        assertEquals(OfferValidity.EXPIRED, OfferValidityEvaluator.evaluate(offer, endsAt))
        assertFalse(offer.isActiveAt(endsAt))
    }

    @Test
    fun afterEnd_isExpired() {
        val offer = testOffer(startsAt = startsAt, endsAt = endsAt)

        val result = OfferValidityEvaluator.evaluate(offer, endsAt.plusMillis(1))

        assertEquals(OfferValidity.EXPIRED, result)
    }
}
