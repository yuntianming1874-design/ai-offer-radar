package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceInfoDao {
    @Query("SELECT * FROM sources WHERE id = :sourceId")
    suspend fun getById(sourceId: String): SourceInfoEntity?

    @Query("SELECT * FROM sources WHERE id IN (:sourceIds)")
    suspend fun getByIds(sourceIds: List<String>): List<SourceInfoEntity>

    @Query("SELECT * FROM sources")
    fun observeSources(): Flow<List<SourceInfoEntity>>

    @Upsert
    suspend fun upsertAll(sources: List<SourceInfoEntity>)
}
