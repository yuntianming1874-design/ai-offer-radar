package com.aioffer.radar

import android.content.Context
import androidx.room.Room
import com.aioffer.radar.core.browser.AndroidOfficialOfferLauncher
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.datastore.AppSettingsStore
import com.aioffer.radar.core.datastore.createAppSettingsStore
import com.aioffer.radar.core.datastore.createDemoStateStore
import com.aioffer.radar.core.network.AndroidNetworkMonitor
import com.aioffer.radar.core.network.DemoRemoteDataSource
import com.aioffer.radar.core.network.GlmCodingPlanRemoteDataSource
import com.aioffer.radar.core.notification.AndroidNotificationSender
import com.aioffer.radar.core.notification.NotificationDispatcher
import com.aioffer.radar.core.notification.OfferNotificationChannels
import com.aioffer.radar.core.push.NoOpPushNotificationService
import com.aioffer.radar.core.push.PushNotificationService
import com.aioffer.radar.core.sync.OfferSyncScheduler
import com.aioffer.radar.core.time.timeTicker
import com.aioffer.radar.data.LocalRadarRepository
import com.aioffer.radar.data.NotifyingSyncService
import com.aioffer.radar.data.OfferSyncService
import com.aioffer.radar.data.SettingsAwareSyncService
import com.aioffer.radar.data.SyncService
import com.aioffer.radar.feature.detail.DetailViewModel
import com.aioffer.radar.feature.home.HomeViewModel
import com.aioffer.radar.feature.settings.SettingsViewModel
import com.aioffer.radar.feature.statistics.StatisticsViewModel
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database = Room.databaseBuilder(
        appContext,
        RadarDatabase::class.java,
        RadarDatabase.DATABASE_NAME
    ).addMigrations(RadarDatabase.MIGRATION_1_2).build()

    private val clock = Clock.systemUTC()
    private val nowTicker = timeTicker(clock)
    val appSettingsStore: AppSettingsStore = createAppSettingsStore(appContext)
    private val demoRemoteDataSource = DemoRemoteDataSource(
        stateStore = createDemoStateStore(appContext)
    )

    /**
     * The official slot reads the GLM Coding Plan snapshot from the local
     * rendering collector. It uses its own source ID and source metadata, so
     * demo payloads can never be persisted as official data.
     */
    private val officialRemoteDataSource = GlmCodingPlanRemoteDataSource()
    private val repository = LocalRadarRepository(database)
    private val demoSyncService = OfferSyncService(
        remoteDataSource = demoRemoteDataSource,
        database = database,
        repository = repository,
        clock = clock
    )
    private val officialSyncService = OfferSyncService(
        remoteDataSource = officialRemoteDataSource,
        database = database,
        repository = repository,
        clock = clock
    )
    private val notificationDispatcher = NotificationDispatcher(
        database = database,
        sender = AndroidNotificationSender(appContext),
        clock = clock
    )
    val syncService: SyncService = NotifyingSyncService(
        delegate = SettingsAwareSyncService(
            settingsStore = appSettingsStore,
            demoSyncService = demoSyncService,
            officialSyncService = officialSyncService
        ),
        notificationDispatcher = notificationDispatcher
    )
    val pushNotificationService: PushNotificationService = NoOpPushNotificationService()
    private val networkMonitor = AndroidNetworkMonitor(appContext)
    private val officialLinkLauncher = AndroidOfficialOfferLauncher(appContext)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        OfferNotificationChannels.create(appContext)
        // Honour the persisted auto-sync preference instead of scheduling
        // unconditionally on every process start.
        applicationScope.launch {
            appSettingsStore.settings
                .catch { emit(com.aioffer.radar.core.model.AppSettings()) }
                .map { it.autoSyncEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    OfferSyncScheduler.setEnabled(appContext, enabled)
                }
        }
    }

    val demoSourceId: String = demoRemoteDataSource.sourceId
    val officialSourceId: String = officialRemoteDataSource.sourceId
    val officialAdapterConfigured: Boolean = true

    fun homeViewModelFactory() = HomeViewModel.factory(
        repository = repository,
        syncService = syncService,
        networkMonitor = networkMonitor,
        clock = clock,
        settingsStore = appSettingsStore,
        demoSourceId = demoSourceId,
        officialSourceId = officialSourceId,
        officialAdapterConfigured = officialAdapterConfigured,
        demoRemoteDataSource = demoRemoteDataSource,
        nowTicker = nowTicker
    )

    fun settingsViewModelFactory() = SettingsViewModel.factory(
        settingsStore = appSettingsStore,
        repository = repository,
        syncService = syncService,
        networkMonitor = networkMonitor,
        demoSourceId = demoSourceId,
        officialSourceId = officialSourceId,
        officialAdapterConfigured = officialAdapterConfigured
    )

    fun detailViewModelFactory(productId: String) = DetailViewModel.factory(
        productId = productId,
        repository = repository,
        clock = clock,
        officialLinkLauncher = officialLinkLauncher,
        allowHttp = false,
        nowTicker = nowTicker
    )

    fun statisticsViewModelFactory() = StatisticsViewModel.factory(repository)
}
