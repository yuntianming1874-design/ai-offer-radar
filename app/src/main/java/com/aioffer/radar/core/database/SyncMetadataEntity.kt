package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_metadata",
    indices = [Index(value = ["status"], name = "index_sync_metadata_status")]
)
data class SyncMetadataEntity(
    @PrimaryKey
    val sourceId: String,
    val status: String,
    val baselineEstablished: Boolean,
    val lastStartedAtEpochMillis: Long?,
    val lastFinishedAtEpochMillis: Long?,
    val errorMessage: String?,
    val lastSuccessfulSyncAtEpochMillis: Long?
)
