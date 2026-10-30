package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "official_click_events",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"], name = "index_clicks_product_id"),
        Index(value = ["offerId"], name = "index_clicks_offer_id"),
        Index(
            value = ["productId", "clickedAtEpochMillis"],
            name = "index_clicks_product_time"
        )
    ]
)
data class OfficialClickEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: String,
    val offerId: String,
    val clickedAtEpochMillis: Long
)
