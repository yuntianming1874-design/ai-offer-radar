package com.aioffer.radar.core.testing

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.OfferType
import com.aioffer.radar.core.model.PlanType
import com.aioffer.radar.core.model.Price
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.Region
import com.aioffer.radar.core.model.SourceInfo
import java.time.Instant

internal val BASE_COMPARISON_KEY = ComparisonKey(
    productId = "kimi",
    planId = "kimi-standard",
    region = Region.MAINLAND_CHINA,
    currency = Currency.CNY,
    billingPeriod = BillingPeriod.MONTHLY,
    taxIncluded = true
)

internal fun testMoney(
    amountMinor: Long,
    currency: Currency = Currency.CNY
) = Money(amountMinor, currency)

internal fun testPrice(
    actualAmountMinor: Long,
    originalAmountMinor: Long = actualAmountMinor,
    monthlyEquivalent: Money? = null,
    currency: Currency = Currency.CNY
) = Price(
    originalAmount = testMoney(originalAmountMinor, currency),
    actualAmount = testMoney(actualAmountMinor, currency),
    monthlyEquivalent = monthlyEquivalent
)

internal fun testSnapshot(
    id: String = "snapshot-1",
    key: ComparisonKey = BASE_COMPARISON_KEY,
    planType: PlanType = PlanType.STANDARD,
    price: Price = testPrice(10_000),
    observedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
) = PriceSnapshot(
    id = id,
    comparisonKey = key,
    planType = planType,
    price = price,
    observedAt = observedAt
)

internal fun testOffer(
    id: String = "offer-1",
    key: ComparisonKey = BASE_COMPARISON_KEY,
    planType: PlanType = PlanType.STANDARD,
    price: Price = testPrice(10_000),
    startsAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    endsAt: Instant? = null
) = Offer(
    id = id,
    comparisonKey = key,
    planType = planType,
    offerType = OfferType.PROMOTION,
    title = "Demo offer",
    discountDescription = "Demo only",
    price = price,
    startsAt = startsAt,
    endsAt = endsAt,
    source = SourceInfo(
        id = "demo",
        type = DataSourceType.DEMO,
        sourceUrl = null,
        titleOrSummary = "Demo data",
        fetchedAt = startsAt
    ),
    updatedAt = startsAt
)
