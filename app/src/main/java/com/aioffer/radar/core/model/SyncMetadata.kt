package com.aioffer.radar.core.model

import java.time.Instant

enum class SyncStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED
}

data class SyncMetadata(
    val sourceId: String,
    val status: SyncStatus,
    val baselineEstablished: Boolean,
    val lastStartedAt: Instant?,
    val lastFinishedAt: Instant?,
    val lastSuccessfulSyncAt: Instant?,
    val errorMessage: String?
)
