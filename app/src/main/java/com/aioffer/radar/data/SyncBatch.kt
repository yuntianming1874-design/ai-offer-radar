package com.aioffer.radar.data

import com.aioffer.radar.core.model.NotificationEvent
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.Plan
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.model.SourceInfo
import com.aioffer.radar.core.model.SyncMetadata

data class SyncBatch(
    val products: List<Product> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val offers: List<Offer> = emptyList(),
    val snapshots: List<PriceSnapshot> = emptyList(),
    val notificationEvents: List<NotificationEvent> = emptyList(),
    val sources: List<SourceInfo> = emptyList(),
    val metadata: SyncMetadata
)
