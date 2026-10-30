package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plans",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"], name = "index_plans_product_id"),
        Index(value = ["productId", "type"], name = "index_plans_product_type")
    ]
)
data class PlanEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val name: String,
    val type: String
)
