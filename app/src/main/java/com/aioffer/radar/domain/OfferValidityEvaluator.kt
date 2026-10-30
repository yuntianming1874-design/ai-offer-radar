package com.aioffer.radar.domain

import com.aioffer.radar.core.model.Offer
import java.time.Instant

enum class OfferValidity {
    NOT_YET_EFFECTIVE,
    ACTIVE,
    EXPIRED
}

object OfferValidityEvaluator {
    fun evaluate(offer: Offer, now: Instant): OfferValidity = when {
        now.isBefore(offer.startsAt) -> OfferValidity.NOT_YET_EFFECTIVE
        offer.endsAt != null && !now.isBefore(offer.endsAt) -> OfferValidity.EXPIRED
        else -> OfferValidity.ACTIVE
    }
}

fun Offer.isActiveAt(now: Instant): Boolean =
    OfferValidityEvaluator.evaluate(this, now) == OfferValidity.ACTIVE
