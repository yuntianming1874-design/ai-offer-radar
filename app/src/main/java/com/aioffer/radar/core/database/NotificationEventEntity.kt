package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_events",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"], name = "index_notifications_product_id"),
        Index(value = ["offerId"], name = "index_notifications_offer_id"),
        Index(value = ["status"], name = "index_notifications_status")
    ]
)
data class NotificationEventEntity(
    @PrimaryKey
    val eventId: String,
    val productId: String,
    val offerId: String?,
    val eventType: String,
    val createdAtEpochMillis: Long,
    val status: String,
    val sentAtEpochMillis: Long?
)
