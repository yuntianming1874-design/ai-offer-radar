package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    indices = [Index(value = ["type"], name = "index_sources_type")]
)
data class SourceInfoEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val sourceUrl: String?,
    val titleOrSummary: String,
    val fetchedAtEpochMillis: Long
)
