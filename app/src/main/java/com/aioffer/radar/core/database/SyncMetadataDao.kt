package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE sourceId = :sourceId")
    fun observeBySourceId(sourceId: String): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): SyncMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)
}
