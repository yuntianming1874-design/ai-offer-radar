package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"], name = "index_price_snapshots_product_id"),
        Index(value = ["planId"], name = "index_price_snapshots_plan_id"),
        Index(
            value = [
                "productId",
                "planId",
                "regionCode",
                "currencyCode",
                "billingPeriod",
                "taxIncluded",
                "observedAtEpochMillis"
            ],
            name = "index_price_snapshots_comparison_time"
        ),
        Index(
            value = [
                "productId",
                "planId",
                "regionCode",
                "currencyCode",
                "billingPeriod",
                "taxIncluded",
                "observedAtEpochMillis",
                "actualAmountMinor"
            ],
            unique = true,
            name = "index_price_snapshots_unique_observation"
        )
    ]
)
data class PriceSnapshotEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val planId: String,
    val planType: String,
    val regionCode: String,
    val currencyCode: String,
    val billingPeriod: String,
    val taxIncluded: Boolean,
    val originalAmountMinor: Long,
    val actualAmountMinor: Long,
    val monthlyEquivalentMinor: Long?,
    val observedAtEpochMillis: Long
)
