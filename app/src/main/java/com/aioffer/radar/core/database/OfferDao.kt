package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers WHERE productId = :productId ORDER BY updatedAtEpochMillis DESC")
    fun observeByProductId(productId: String): Flow<List<OfferEntity>>

    @Query(
        """
        SELECT * FROM offers
        WHERE startsAtEpochMillis <= :nowEpochMillis
            AND (endsAtEpochMillis IS NULL OR endsAtEpochMillis > :nowEpochMillis)
        ORDER BY productId ASC, updatedAtEpochMillis DESC
        """
    )
    fun observeActiveOffers(nowEpochMillis: Long): Flow<List<OfferEntity>>

    @Query(
        """
        SELECT * FROM offers
        WHERE productId = :productId
            AND startsAtEpochMillis <= :nowEpochMillis
            AND (endsAtEpochMillis IS NULL OR endsAtEpochMillis > :nowEpochMillis)
        ORDER BY updatedAtEpochMillis DESC
        """
    )
    suspend fun getActiveOffers(productId: String, nowEpochMillis: Long): List<OfferEntity>

    @Query("SELECT * FROM offers")
    suspend fun getAll(): List<OfferEntity>

    @Query("SELECT * FROM offers WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): List<OfferEntity>

    @Query("SELECT officialUrl FROM offers WHERE remoteId = :offerId AND productId = :productId")
    suspend fun getOfficialUrl(productId: String, offerId: String): String?

    @Query("SELECT COUNT(*) FROM offers")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(offers: List<OfferEntity>)
}
