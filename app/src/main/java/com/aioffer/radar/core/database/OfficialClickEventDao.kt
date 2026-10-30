package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ProductClickCountRow(
    val productId: String,
    val productName: String,
    val clickCount: Int
)

@Dao
interface OfficialClickEventDao {
    @Insert
    suspend fun insert(event: OfficialClickEventEntity)

    @Query(
        """
        SELECT clicks.productId AS productId, products.name AS productName,
            COUNT(*) AS clickCount
        FROM official_click_events AS clicks
        INNER JOIN products AS products ON products.id = clicks.productId
        GROUP BY clicks.productId, products.name
        ORDER BY products.name COLLATE NOCASE ASC
        """
    )
    fun observeProductClickCounts(): Flow<List<ProductClickCountRow>>

    @Query("SELECT COUNT(*) FROM official_click_events WHERE productId = :productId")
    suspend fun countForProduct(productId: String): Int

    @Query("DELETE FROM official_click_events")
    suspend fun clear()
}
