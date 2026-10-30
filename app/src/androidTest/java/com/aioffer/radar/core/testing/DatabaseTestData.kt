package com.aioffer.radar.core.testing

import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.OfferType
import com.aioffer.radar.core.model.Plan
import com.aioffer.radar.core.model.PlanType
import com.aioffer.radar.core.model.Price
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.model.Region
import com.aioffer.radar.core.model.SourceInfo
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.data.SyncBatch
import java.time.Instant

internal val DATABASE_TEST_KEY = ComparisonKey(
    productId = "kimi",
    planId = "kimi-standard",
    region = Region.MAINLAND_CHINA,
    currency = Currency.CNY,
    billingPeriod = BillingPeriod.MONTHLY,
    taxIncluded = true
)

internal val DATABASE_TEST_START: Instant = Instant.parse("2026-01-01T00:00:00Z")
internal val DATABASE_TEST_NOW: Instant = Instant.parse("2026-06-01T00:00:00Z")

internal fun databaseProduct(id: String) = Product(
    id = id,
    name = id.uppercase(),
    logoUrl = null,
    isDemo = true
)

internal fun databasePlan(productId: String) = Plan(
    id = "$productId-standard",
    productId = productId,
    name = "Standard",
    type = PlanType.STANDARD
)

internal fun databaseSource() = SourceInfo(
    id = "demo",
    type = DataSourceType.DEMO,
    sourceUrl = null,
    titleOrSummary = "Deterministic demo data",
    fetchedAt = DATABASE_TEST_NOW
)

internal fun databaseOffer(
    id: String = "offer-1",
    key: ComparisonKey = DATABASE_TEST_KEY,
    actualAmountMinor: Long = 10_000,
    startsAt: Instant = DATABASE_TEST_START,
    endsAt: Instant? = null,
    updatedAt: Instant = DATABASE_TEST_NOW
) = Offer(
    id = id,
    comparisonKey = key,
    planType = PlanType.STANDARD,
    offerType = OfferType.PROMOTION,
    title = "Demo offer $id",
    discountDescription = "Demo only",
    price = Price(
        originalAmount = Money(12_000, key.currency),
        actualAmount = Money(actualAmountMinor, key.currency)
    ),
    startsAt = startsAt,
    endsAt = endsAt,
    source = databaseSource(),
    updatedAt = updatedAt,
    officialUrl = "https://example.com/subscribe"
)

internal fun databaseSnapshot(
    id: String = "snapshot-1",
    key: ComparisonKey = DATABASE_TEST_KEY,
    actualAmountMinor: Long = 10_000,
    observedAt: Instant = DATABASE_TEST_NOW
) = PriceSnapshot(
    id = id,
    comparisonKey = key,
    planType = PlanType.STANDARD,
    price = Price(
        originalAmount = Money(12_000, key.currency),
        actualAmount = Money(actualAmountMinor, key.currency)
    ),
    observedAt = observedAt
)

internal fun databaseMetadata() = SyncMetadata(
    sourceId = "demo",
    status = SyncStatus.SUCCESS,
    baselineEstablished = true,
    lastStartedAt = DATABASE_TEST_NOW,
    lastFinishedAt = DATABASE_TEST_NOW,
    lastSuccessfulSyncAt = DATABASE_TEST_NOW,
    errorMessage = null
)

internal fun databaseSeedBatch(
    products: List<Product> = listOf(
        databaseProduct("kimi"),
        databaseProduct("deepseek"),
        databaseProduct("glm")
    ),
    offers: List<Offer> = listOf(databaseOffer()),
    snapshots: List<PriceSnapshot> = listOf(databaseSnapshot())
) = SyncBatch(
    products = products,
    plans = products.map { databasePlan(it.id) },
    offers = offers,
    snapshots = snapshots,
    sources = listOf(databaseSource()),
    metadata = databaseMetadata()
)
