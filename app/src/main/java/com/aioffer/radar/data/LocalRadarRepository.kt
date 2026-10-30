package com.aioffer.radar.data

import androidx.room.withTransaction
import com.aioffer.radar.core.database.OfficialClickEventEntity
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.database.UserSubscriptionEntity
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.core.model.ProductDetail
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.domain.HistoricalLowestEvaluator
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LocalRadarRepository(
    private val database: RadarDatabase
) : RadarRepository {
    override fun observeHomeProducts(now: Instant): Flow<List<HomeProduct>> = combine(
        database.productDao().observeProducts(),
        database.offerDao().observeActiveOffers(now.toEpochMilli()),
        database.userSubscriptionDao().observeSubscribedProductIds(),
        database.sourceInfoDao().observeSources(),
        database.priceSnapshotDao().observeAll()
    ) { products, offers, subscribedIds, sources, snapshots ->
        val sourcesById = sources.associateBy { it.id }
        val priceSnapshots = snapshots.map { it.toDomain() }
        products.map { product ->
            // combine 的多个查询不是同一快照：offers 可能先于 sources 发出。
            // 缺少来源的中间态直接跳过，等下一次发射补齐，不能让首页崩溃。
            val activeOffers = offers.filter { it.productId == product.id }.mapNotNull { offer ->
                sourcesById[offer.sourceId]?.let(offer::toDomain)
            }
            HomeProduct(
                product = product.toDomain(),
                activeOffers = activeOffers,
                isSubscribed = product.id in subscribedIds,
                historicalLowestOfferIds = activeOffers.filter { offer ->
                    HistoricalLowestEvaluator.isHistoricalLowest(
                        offer,
                        priorHistory(offer, priceSnapshots)
                    )
                }.mapTo(mutableSetOf()) { it.id }
            )
        }
    }

    override fun observeProductDetail(productId: String): Flow<ProductDetail?> = combine(
        database.productDao().observeById(productId),
        database.planDao().observePlansForProduct(productId),
        database.offerDao().observeByProductId(productId),
        database.sourceInfoDao().observeSources(),
        database.priceSnapshotDao().observeByProductId(productId)
    ) { product, plans, offers, sources, snapshots ->
        product ?: return@combine null
        val sourcesById = sources.associateBy { it.id }
        val domainOffers = offers.mapNotNull { offer ->
            sourcesById[offer.sourceId]?.let(offer::toDomain)
        }
        val priceSnapshots = snapshots.map { it.toDomain() }
        ProductDetail(
            product = product.toDomain(),
            plans = plans.map { it.toDomain() },
            offers = domainOffers,
            historicalLowestOfferIds = domainOffers.filter { offer ->
                HistoricalLowestEvaluator.isHistoricalLowest(
                    offer,
                    priorHistory(offer, priceSnapshots)
                )
            }.mapTo(mutableSetOf()) { it.id }
        )
    }

    /**
     * The snapshot created for the candidate's current observation must not be used as
     * evidence for the "historical lowest" badge. Otherwise a group with a single
     * observation would be marked as its own historical low.
     *
     * The latest snapshot in the same group whose amount equals the candidate amount
     * represents that current observation. It is removed from the comparison set; any
     * other offer in the same comparison group remains eligible as history.
     */
    private fun priorHistory(
        candidate: Offer,
        snapshots: List<PriceSnapshot>
    ): List<PriceSnapshot> {
        val sameGroup = snapshots.filter { it.comparisonKey == candidate.comparisonKey }
        val currentObservation = sameGroup
            .filter { it.price.actualAmount == candidate.price.actualAmount }
            .maxByOrNull { it.observedAt }
        return if (currentObservation == null) {
            sameGroup
        } else {
            sameGroup.filterNot { it.id == currentObservation.id }
        }
    }

    override fun observePriceHistory(key: ComparisonKey): Flow<List<PriceSnapshot>> =
        database.priceSnapshotDao().observeHistory(
            productId = key.productId,
            planId = key.planId,
            regionCode = key.region.code,
            currencyCode = key.currency.name,
            billingPeriod = key.billingPeriod.name,
            taxIncluded = key.taxIncluded
        ).map { snapshots -> snapshots.map { it.toDomain() } }

    override fun observeSubscribedProductIds(): Flow<List<String>> =
        database.userSubscriptionDao().observeSubscribedProductIds()

    override fun observeSyncMetadata(sourceId: String): Flow<SyncMetadata?> =
        database.syncMetadataDao().observeBySourceId(sourceId).map { metadata ->
            metadata?.toDomain()
        }

    override fun observeClickStatistics(): Flow<List<ProductClickCount>> =
        database.officialClickEventDao().observeProductClickCounts().map { counts ->
            counts.map { ProductClickCount(it.productId, it.productName, it.clickCount) }
        }

    override suspend fun getActiveOffers(productId: String, now: Instant): List<Offer> =
        database.offerDao().getActiveOffers(productId, now.toEpochMilli()).map { offer ->
            offer.toDomain(requireNotNull(database.sourceInfoDao().getById(offer.sourceId)))
        }

    override suspend fun getOfficialOfferUrl(productId: String, offerId: String): String? =
        database.offerDao().getOfficialUrl(productId, offerId)

    override suspend fun applySync(batch: SyncBatch) {
        database.withTransaction {
            val sources = (batch.sources + batch.offers.map { it.source }).distinctBy { it.id }
            // A source row is the trust label of every offer/snapshot that
            // references it. Changing DEMO -> OFFICIAL (or back) would relabel
            // already-stored history, so the type is immutable once persisted.
            val existingSources = database.sourceInfoDao()
                .getByIds(sources.map { it.id })
                .associateBy { it.id }
            sources.forEach { source ->
                val existingType = existingSources[source.id]?.type
                check(existingType == null || existingType == source.type.name) {
                    "Data source ${source.id} type is immutable: stored $existingType, " +
                        "incoming ${source.type.name}"
                }
            }
            database.productDao().upsertAll(batch.products.map { it.toEntity() })
            database.planDao().upsertAll(batch.plans.map { it.toEntity() })
            database.sourceInfoDao().upsertAll(sources.map { it.toEntity() })
            database.offerDao().upsertAll(batch.offers.map { it.toEntity() })
            database.priceSnapshotDao().insertAll(batch.snapshots.map { it.toEntity() })
            database.notificationEventDao().insertAllIfAbsent(
                batch.notificationEvents.map { it.toEntity() }
            )
            database.syncMetadataDao().upsert(batch.metadata.toEntity())
        }
    }

    override suspend fun setSubscribed(productId: String, subscribed: Boolean, at: Instant) {
        if (subscribed) {
            database.userSubscriptionDao().subscribe(
                UserSubscriptionEntity(productId, at.toEpochMilli())
            )
        } else {
            database.userSubscriptionDao().unsubscribe(productId)
        }
    }

    override suspend fun subscribeAll(at: Instant) {
        database.userSubscriptionDao().subscribeAll(at.toEpochMilli())
    }

    override suspend fun isSubscribed(productId: String): Boolean =
        database.userSubscriptionDao().isSubscribed(productId)

    override suspend fun recordOfficialClick(productId: String, offerId: String, clickedAt: Instant) {
        database.officialClickEventDao().insert(
            OfficialClickEventEntity(
                productId = productId,
                offerId = offerId,
                clickedAtEpochMillis = clickedAt.toEpochMilli()
            )
        )
    }

    override suspend fun clearClickHistory() {
        database.officialClickEventDao().clear()
    }

    override suspend fun hasNotificationEvent(eventId: String): Boolean =
        database.notificationEventDao().exists(eventId)
}
