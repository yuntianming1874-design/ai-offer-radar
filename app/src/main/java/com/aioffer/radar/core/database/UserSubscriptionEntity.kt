package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserSubscriptionEntity(
    @PrimaryKey
    val productId: String,
    val subscribedAtEpochMillis: Long
)
