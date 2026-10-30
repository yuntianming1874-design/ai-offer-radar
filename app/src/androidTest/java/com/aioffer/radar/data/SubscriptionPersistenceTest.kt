package com.aioffer.radar.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.testing.DATABASE_TEST_NOW
import com.aioffer.radar.core.testing.databaseMetadata
import com.aioffer.radar.core.testing.databasePlan
import com.aioffer.radar.core.testing.databaseProduct
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionPersistenceTest {
    @Test
    fun subscription_survivesDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "subscription-restart-test.db"
        context.deleteDatabase(databaseName)

        try {
            var database = Room.databaseBuilder(context, RadarDatabase::class.java, databaseName)
                .addMigrations(RadarDatabase.MIGRATION_1_2)
                .build()
            runBlocking {
                val product = databaseProduct("kimi")
                LocalRadarRepository(database).applySync(
                    SyncBatch(
                        products = listOf(product),
                        plans = listOf(databasePlan(product.id)),
                        metadata = databaseMetadata()
                    )
                )
                LocalRadarRepository(database).setSubscribed("kimi", true, DATABASE_TEST_NOW)
            }
            database.close()

            database = Room.databaseBuilder(context, RadarDatabase::class.java, databaseName)
                .addMigrations(RadarDatabase.MIGRATION_1_2)
                .build()
            val isSubscribed = runBlocking {
                LocalRadarRepository(database).isSubscribed("kimi")
            }
            database.close()

            assertTrue(isSubscribed)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
