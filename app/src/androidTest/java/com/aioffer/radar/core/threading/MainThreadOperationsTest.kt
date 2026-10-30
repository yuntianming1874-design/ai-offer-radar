package com.aioffer.radar.core.threading

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.datastore.DataStoreAppSettingsStore
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.network.DemoRemoteDataSource
import com.aioffer.radar.data.LocalRadarRepository
import com.aioffer.radar.data.OfferSyncService
import com.aioffer.radar.data.SyncResult
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the data layer is safe to call from a UI coroutine: Room, DataStore
 * and sync work must move their IO off the main thread. StrictMode is installed
 * on the main thread and any violation whose stack touches app code fails the
 * test.
 */
@RunWith(AndroidJUnit4::class)
class MainThreadOperationsTest {
    private lateinit var database: RadarDatabase
    private lateinit var repository: LocalRadarRepository
    private lateinit var syncService: OfferSyncService
    private lateinit var settingsStore: DataStoreAppSettingsStore
    private lateinit var dataStoreScope: CoroutineScope
    private val violations = mutableListOf<Violation>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build()
        repository = LocalRadarRepository(database)
        val clock = Clock.fixed(DemoRemoteDataSource.DEMO_FETCHED_AT, java.time.ZoneOffset.UTC)
        syncService = OfferSyncService(
            remoteDataSource = DemoRemoteDataSource(),
            database = database,
            repository = repository,
            clock = clock
        )
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settingsFile = File(
            context.cacheDir,
            "main-thread-settings-${System.nanoTime()}.preferences_pb"
        )
        settingsStore = DataStoreAppSettingsStore(
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { settingsFile }
            )
        )
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        }
        database.close()
        dataStoreScope.cancel()
    }

    private fun installMainThreadStrictMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val builder = StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.penaltyListener(
                    ContextCompat.getMainExecutor(instrumentation.targetContext)
                ) { violation ->
                    violations += violation
                }
            } else {
                builder.penaltyLog()
            }
            StrictMode.setThreadPolicy(builder.build())
        }
    }

    @Test
    fun databaseSyncAndClickWork_doNotRunDiskIoOnTheMainThread() {
        installMainThreadStrictMode()

        runBlocking(Dispatchers.Main) {
            val syncResult = syncService.sync()
            assertTrue(syncResult is SyncResult.Success)

            val now = DemoRemoteDataSource.DEMO_FETCHED_AT.plusSeconds(60)
            val offers = repository.getActiveOffers("demo-kimi", now)
            assertTrue(offers.isNotEmpty())
            repository.recordOfficialClick("demo-kimi", offers.first().id, now)
            repository.setSubscribed("demo-kimi", true, now)
            assertTrue(repository.isSubscribed("demo-kimi"))
            repository.subscribeAll(now)
            assertTrue(repository.isSubscribed("demo-deepseek"))
            assertTrue(repository.observeHomeProducts(now).first().isNotEmpty())
            assertEquals(1, repository.observeClickStatistics().first().single().clickCount)

            repository.getOfficialOfferUrl("demo-kimi", offers.first().id)
            assertFalse(repository.hasNotificationEvent("missing-event"))
            repository.clearClickHistory()
            assertTrue(repository.observeClickStatistics().first().isEmpty())

            settingsStore.setDataSourceMode(DataSourceMode.OFFICIAL)
            assertEquals(
                DataSourceMode.OFFICIAL,
                settingsStore.settings.first().dataSourceMode
            )
        }

        // Give StrictMode penalty callbacks posted to the main executor a chance
        // to run before asserting.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val appViolations = violations.filter { violation ->
            violation.stackTrace.any { frame ->
                frame.className.startsWith("com.aioffer.radar")
            }
        }
        assertTrue(
            "Unexpected main-thread disk IO:\n" +
                appViolations.joinToString("\n") { it.stackTrace.take(8).joinToString("\n") },
            appViolations.isEmpty()
        )
    }
}
