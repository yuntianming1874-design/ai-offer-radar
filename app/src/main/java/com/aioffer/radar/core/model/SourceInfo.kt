package com.aioffer.radar.core.model

import java.time.Instant

enum class DataSourceType {
    DEMO,
    OFFICIAL_API,
    OFFICIAL_RSS,
    OFFICIAL_WEB
}

data class SourceInfo(
    val id: String,
    val type: DataSourceType,
    val sourceUrl: String?,
    val titleOrSummary: String,
    val fetchedAt: Instant
)
