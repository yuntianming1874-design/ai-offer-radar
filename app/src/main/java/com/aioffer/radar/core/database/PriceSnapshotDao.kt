package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceSnapshotDao {
    @Query(
        """
        SELECT * FROM price_snapshots
        WHERE productId = :productId
            AND planId = :planId
            AND regionCode = :regionCode
            AND currencyCode = :currencyCode
            AND billingPeriod = :billingPeriod
            AND taxIncluded = :taxIncluded
        ORDER BY observedAtEpochMillis ASC
        """
    )
    fun observeHistory(
        productId: String,
        planId: String,
        regionCode: String,
        currencyCode: String,
        billingPeriod: String,
        taxIncluded: Boolean
    ): Flow<List<PriceSnapshotEntity>>

    @Query("SELECT * FROM price_snapshots WHERE productId = :productId ORDER BY observedAtEpochMillis ASC")
    fun observeByProductId(productId: String): Flow<List<PriceSnapshotEntity>>

    @Query("SELECT * FROM price_snapshots ORDER BY observedAtEpochMillis ASC")
    fun observeAll(): Flow<List<PriceSnapshotEntity>>

    @Query("SELECT * FROM price_snapshots ORDER BY observedAtEpochMillis ASC")
    suspend fun getAll(): List<PriceSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(snapshots: List<PriceSnapshotEntity>)
}
