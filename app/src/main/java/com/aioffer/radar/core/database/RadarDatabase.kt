package com.aioffer.radar.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        PlanEntity::class,
        OfferEntity::class,
        PriceSnapshotEntity::class,
        UserSubscriptionEntity::class,
        OfficialClickEventEntity::class,
        NotificationEventEntity::class,
        SourceInfoEntity::class,
        SyncMetadataEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun planDao(): PlanDao
    abstract fun offerDao(): OfferDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao
    abstract fun userSubscriptionDao(): UserSubscriptionDao
    abstract fun officialClickEventDao(): OfficialClickEventDao
    abstract fun notificationEventDao(): NotificationEventDao
    abstract fun sourceInfoDao(): SourceInfoDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        const val DATABASE_NAME = "ai_offer_radar.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE sync_metadata " +
                        "ADD COLUMN lastSuccessfulSyncAtEpochMillis INTEGER"
                )
            }
        }
    }
}
