package com.aioffer.radar.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offers",
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
        ),
        ForeignKey(
            entity = SourceInfoEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"], name = "index_offers_product_id"),
        Index(value = ["planId"], name = "index_offers_plan_id"),
        Index(value = ["sourceId"], name = "index_offers_source_id"),
        Index(
            value = ["sourceId", "remoteId"],
            unique = true,
            name = "index_offers_source_remote_unique"
        ),
        Index(
            value = [
                "productId",
                "planId",
                "regionCode",
                "currencyCode",
                "billingPeriod",
                "taxIncluded",
                "startsAtEpochMillis",
                "endsAtEpochMillis"
            ],
            name = "index_offers_comparison_validity"
        )
    ]
)
data class OfferEntity(
    @PrimaryKey
    val id: String,
    val remoteId: String,
    val productId: String,
    val planId: String,
    val planType: String,
    val offerType: String,
    val title: String,
    val discountDescription: String,
    val regionCode: String,
    val currencyCode: String,
    val billingPeriod: String,
    val taxIncluded: Boolean,
    val originalAmountMinor: Long,
    val actualAmountMinor: Long,
    val monthlyEquivalentMinor: Long?,
    val startsAtEpochMillis: Long,
    val endsAtEpochMillis: Long?,
    val sourceId: String,
    val updatedAtEpochMillis: Long,
    val officialUrl: String?
)
