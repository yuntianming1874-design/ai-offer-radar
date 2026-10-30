package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE productId = :productId ORDER BY name ASC")
    fun observePlansForProduct(productId: String): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE productId = :productId ORDER BY name ASC")
    suspend fun getPlansForProduct(productId: String): List<PlanEntity>

    @Upsert
    suspend fun upsertAll(plans: List<PlanEntity>)
}
