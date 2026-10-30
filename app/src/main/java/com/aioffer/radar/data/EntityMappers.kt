package com.aioffer.radar.data

import com.aioffer.radar.core.database.NotificationEventEntity
import com.aioffer.radar.core.database.OfferEntity
import com.aioffer.radar.core.database.PlanEntity
import com.aioffer.radar.core.database.PriceSnapshotEntity
import com.aioffer.radar.core.database.ProductEntity
import com.aioffer.radar.core.database.SourceInfoEntity
import com.aioffer.radar.core.database.SyncMetadataEntity
import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.NotificationEvent
import com.aioffer.radar.core.model.NotificationEventStatus
import com.aioffer.radar.core.model.NotificationEventType
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
import java.time.Instant

internal fun stableOfferId(sourceId: String, remoteId: String): String = "$sourceId:$remoteId"

internal fun Product.toEntity() = ProductEntity(id, name, logoUrl, isDemo)

internal fun ProductEntity.toDomain() = Product(id, name, logoUrl, isDemo)

internal fun Plan.toEntity() = PlanEntity(id, productId, name, type.name)

internal fun PlanEntity.toDomain() = Plan(id, productId, name, PlanType.valueOf(type))

internal fun SourceInfo.toEntity() = SourceInfoEntity(
    id = id,
    type = type.name,
    sourceUrl = sourceUrl,
    titleOrSummary = titleOrSummary,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli()
)

internal fun SourceInfoEntity.toDomain() = SourceInfo(
    id = id,
    type = DataSourceType.valueOf(type),
    sourceUrl = sourceUrl,
    titleOrSummary = titleOrSummary,
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis)
)

internal fun Offer.toEntity() = OfferEntity(
    id = stableOfferId(source.id, id),
    remoteId = id,
    productId = comparisonKey.productId,
    planId = comparisonKey.planId,
    planType = planType.name,
    offerType = offerType.name,
    title = title,
    discountDescription = discountDescription,
    regionCode = comparisonKey.region.code,
    currencyCode = comparisonKey.currency.name,
    billingPeriod = comparisonKey.billingPeriod.name,
    taxIncluded = comparisonKey.taxIncluded,
    originalAmountMinor = price.originalAmount.amountMinor,
    actualAmountMinor = price.actualAmount.amountMinor,
    monthlyEquivalentMinor = price.monthlyEquivalent?.amountMinor,
    startsAtEpochMillis = startsAt.toEpochMilli(),
    endsAtEpochMillis = endsAt?.toEpochMilli(),
    sourceId = source.id,
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    officialUrl = officialUrl
)

internal fun OfferEntity.toDomain(source: SourceInfoEntity) = Offer(
    id = remoteId,
    comparisonKey = ComparisonKey(
        productId = productId,
        planId = planId,
        region = Region(regionCode),
        currency = Currency.valueOf(currencyCode),
        billingPeriod = BillingPeriod.valueOf(billingPeriod),
        taxIncluded = taxIncluded
    ),
    planType = PlanType.valueOf(planType),
    offerType = OfferType.valueOf(offerType),
    title = title,
    discountDescription = discountDescription,
    price = Price(
        originalAmount = Money(originalAmountMinor, Currency.valueOf(currencyCode)),
        actualAmount = Money(actualAmountMinor, Currency.valueOf(currencyCode)),
        monthlyEquivalent = monthlyEquivalentMinor?.let {
            Money(it, Currency.valueOf(currencyCode))
        }
    ),
    startsAt = Instant.ofEpochMilli(startsAtEpochMillis),
    endsAt = endsAtEpochMillis?.let(Instant::ofEpochMilli),
    source = source.toDomain(),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    officialUrl = officialUrl
)

internal fun PriceSnapshot.toEntity() = PriceSnapshotEntity(
    id = id,
    productId = comparisonKey.productId,
    planId = comparisonKey.planId,
    planType = planType.name,
    regionCode = comparisonKey.region.code,
    currencyCode = comparisonKey.currency.name,
    billingPeriod = comparisonKey.billingPeriod.name,
    taxIncluded = comparisonKey.taxIncluded,
    originalAmountMinor = price.originalAmount.amountMinor,
    actualAmountMinor = price.actualAmount.amountMinor,
    monthlyEquivalentMinor = price.monthlyEquivalent?.amountMinor,
    observedAtEpochMillis = observedAt.toEpochMilli()
)

internal fun PriceSnapshotEntity.toDomain() = PriceSnapshot(
    id = id,
    comparisonKey = ComparisonKey(
        productId = productId,
        planId = planId,
        region = Region(regionCode),
        currency = Currency.valueOf(currencyCode),
        billingPeriod = BillingPeriod.valueOf(billingPeriod),
        taxIncluded = taxIncluded
    ),
    planType = PlanType.valueOf(planType),
    price = Price(
        originalAmount = Money(originalAmountMinor, Currency.valueOf(currencyCode)),
        actualAmount = Money(actualAmountMinor, Currency.valueOf(currencyCode)),
        monthlyEquivalent = monthlyEquivalentMinor?.let {
            Money(it, Currency.valueOf(currencyCode))
        }
    ),
    observedAt = Instant.ofEpochMilli(observedAtEpochMillis)
)

internal fun NotificationEvent.toEntity() = NotificationEventEntity(
    eventId = eventId,
    productId = productId,
    offerId = offerId,
    eventType = eventType.name,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    status = status.name,
    sentAtEpochMillis = sentAt?.toEpochMilli()
)

internal fun NotificationEventEntity.toDomain() = NotificationEvent(
    eventId = eventId,
    productId = productId,
    offerId = offerId,
    eventType = NotificationEventType.valueOf(eventType),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    status = NotificationEventStatus.valueOf(status),
    sentAt = sentAtEpochMillis?.let(Instant::ofEpochMilli)
)

internal fun SyncMetadata.toEntity() = SyncMetadataEntity(
    sourceId = sourceId,
    status = status.name,
    baselineEstablished = baselineEstablished,
    lastStartedAtEpochMillis = lastStartedAt?.toEpochMilli(),
    lastFinishedAtEpochMillis = lastFinishedAt?.toEpochMilli(),
    errorMessage = errorMessage,
    lastSuccessfulSyncAtEpochMillis = lastSuccessfulSyncAt?.toEpochMilli()
)

internal fun SyncMetadataEntity.toDomain() = SyncMetadata(
    sourceId = sourceId,
    status = SyncStatus.valueOf(status),
    baselineEstablished = baselineEstablished,
    lastStartedAt = lastStartedAtEpochMillis?.let(Instant::ofEpochMilli),
    lastFinishedAt = lastFinishedAtEpochMillis?.let(Instant::ofEpochMilli),
    lastSuccessfulSyncAt = lastSuccessfulSyncAtEpochMillis?.let(Instant::ofEpochMilli),
    errorMessage = errorMessage
)
