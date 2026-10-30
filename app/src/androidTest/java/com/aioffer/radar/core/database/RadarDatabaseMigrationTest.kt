package com.aioffer.radar.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RadarDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesSubscriptionsAndSyncMetadata() {
        val databaseName = "migration-test.db"
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO products (id, name, logoUrl, isDemo) " +
                    "VALUES ('kimi', 'Kimi', NULL, 1)"
            )
            execSQL(
                "INSERT INTO user_subscriptions (productId, subscribedAtEpochMillis) " +
                    "VALUES ('kimi', 1000)"
            )
            execSQL(
                "INSERT INTO sync_metadata " +
                    "(sourceId, status, baselineEstablished, lastStartedAtEpochMillis, " +
                    "lastFinishedAtEpochMillis, errorMessage) " +
                    "VALUES ('demo', 'SUCCESS', 1, 1000, 2000, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            RadarDatabase.MIGRATION_1_2
        )

        migrated.query("SELECT productId FROM user_subscriptions WHERE productId = 'kimi'").use {
            assertTrue(it.moveToFirst())
            assertEquals("kimi", it.getString(0))
        }
        migrated.query(
            "SELECT status, lastSuccessfulSyncAtEpochMillis FROM sync_metadata " +
                "WHERE sourceId = 'demo'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("SUCCESS", it.getString(0))
            assertNull(it.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migrate1To2_preservesOffersSnapshotsAndSourceFetchMetadata() {
        val databaseName = "migration-business-data.db"
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO products (id, name, logoUrl, isDemo) " +
                    "VALUES ('kimi', 'Kimi', NULL, 1)"
            )
            execSQL(
                "INSERT INTO plans (id, productId, name, type) " +
                    "VALUES ('kimi-standard', 'kimi', 'Standard', 'STANDARD')"
            )
            execSQL(
                "INSERT INTO sources " +
                    "(id, type, sourceUrl, titleOrSummary, fetchedAtEpochMillis) " +
                    "VALUES ('demo', 'DEMO', NULL, 'Demo data', 1000)"
            )
            execSQL(
                "INSERT INTO offers " +
                    "(id, remoteId, productId, planId, planType, offerType, title, " +
                    "discountDescription, regionCode, currencyCode, billingPeriod, " +
                    "taxIncluded, originalAmountMinor, actualAmountMinor, " +
                    "monthlyEquivalentMinor, startsAtEpochMillis, endsAtEpochMillis, " +
                    "sourceId, updatedAtEpochMillis, officialUrl) VALUES " +
                    "('demo:offer-1', 'offer-1', 'kimi', 'kimi-standard', 'STANDARD', " +
                    "'PROMOTION', 'Demo offer', 'Demo only', 'CN', 'CNY', 'MONTHLY', 1, " +
                    "12000, 10000, 10000, 1000, NULL, 'demo', 1000, " +
                    "'https://example.com/official')"
            )
            execSQL(
                "INSERT INTO price_snapshots " +
                    "(id, productId, planId, planType, regionCode, currencyCode, " +
                    "billingPeriod, taxIncluded, originalAmountMinor, actualAmountMinor, " +
                    "monthlyEquivalentMinor, observedAtEpochMillis) VALUES " +
                    "('snapshot-1', 'kimi', 'kimi-standard', 'STANDARD', 'CN', 'CNY', " +
                    "'MONTHLY', 1, 12000, 8000, 8000, 1000)"
            )
            execSQL(
                "INSERT INTO sync_metadata " +
                    "(sourceId, status, baselineEstablished, lastStartedAtEpochMillis, " +
                    "lastFinishedAtEpochMillis, errorMessage) " +
                    "VALUES ('demo', 'SUCCESS', 1, 1000, 2000, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            RadarDatabase.MIGRATION_1_2
        )

        migrated.query(
            "SELECT officialUrl, actualAmountMinor, sourceId FROM offers " +
                "WHERE id = 'demo:offer-1'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("https://example.com/official", it.getString(0))
            assertEquals(10_000L, it.getLong(1))
            assertEquals("demo", it.getString(2))
        }
        migrated.query(
            "SELECT actualAmountMinor, monthlyEquivalentMinor FROM price_snapshots " +
                "WHERE id = 'snapshot-1'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(8_000L, it.getLong(0))
            assertEquals(8_000L, it.getLong(1))
        }
        migrated.query(
            "SELECT type, fetchedAtEpochMillis FROM sources WHERE id = 'demo'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("DEMO", it.getString(0))
            assertEquals(1_000L, it.getLong(1))
        }
        migrated.query(
            "SELECT status, lastSuccessfulSyncAtEpochMillis FROM sync_metadata " +
                "WHERE sourceId = 'demo'"
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("SUCCESS", it.getString(0))
            assertNull(it.getString(1))
        }
        migrated.close()
    }
}
