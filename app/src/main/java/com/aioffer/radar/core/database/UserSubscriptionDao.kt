package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun subscribe(subscription: UserSubscriptionEntity)

    @Query("DELETE FROM user_subscriptions WHERE productId = :productId")
    suspend fun unsubscribe(productId: String)

    @Query(
        """
        INSERT OR IGNORE INTO user_subscriptions (productId, subscribedAtEpochMillis)
        SELECT id, :subscribedAtEpochMillis FROM products
        """
    )
    suspend fun subscribeAll(subscribedAtEpochMillis: Long)

    @Query("SELECT productId FROM user_subscriptions")
    fun observeSubscribedProductIds(): Flow<List<String>>

    @Query("SELECT productId FROM user_subscriptions")
    suspend fun getSubscribedProductIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM user_subscriptions WHERE productId = :productId)")
    suspend fun isSubscribed(productId: String): Boolean
}
