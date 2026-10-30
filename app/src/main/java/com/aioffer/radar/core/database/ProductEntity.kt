package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["name"], name = "index_products_name")]
)
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val logoUrl: String?,
    val isDemo: Boolean
)
