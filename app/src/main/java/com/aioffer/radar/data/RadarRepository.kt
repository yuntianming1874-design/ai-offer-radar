package com.aioffer.radar.data

import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.core.model.ProductDetail
import com.aioffer.radar.core.model.SyncMetadata
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface RadarRepository {
    fun observeHomeProducts(now: Instant): Flow<List<HomeProduct>>

    fun observeProductDetail(productId: String): Flow<ProductDetail?>

    fun observePriceHistory(key: ComparisonKey): Flow<List<PriceSnapshot>>

    fun observeSubscribedProductIds(): Flow<List<String>>

    fun observeSyncMetadata(sourceId: String): Flow<SyncMetadata?>

    fun observeClickStatistics(): Flow<List<ProductClickCount>>

    suspend fun getActiveOffers(productId: String, now: Instant): List<Offer>

    suspend fun getOfficialOfferUrl(productId: String, offerId: String): String?

    suspend fun applySync(batch: SyncBatch)

    suspend fun setSubscribed(productId: String, subscribed: Boolean, at: Instant)

    suspend fun subscribeAll(at: Instant)

    suspend fun isSubscribed(productId: String): Boolean

    suspend fun recordOfficialClick(productId: String, offerId: String, clickedAt: Instant)

    suspend fun clearClickHistory()

    suspend fun hasNotificationEvent(eventId: String): Boolean
}
