package com.aioffer.radar.feature.settings

import com.aioffer.radar.core.datastore.AppSettingsStore
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.NotificationPromptState
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.core.model.ProductDetail
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.core.network.NetworkMonitor
import com.aioffer.radar.data.RadarRepository
import com.aioffer.radar.data.SyncBatch
import com.aioffer.radar.data.SyncResult
import com.aioffer.radar.data.SyncService
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-06-01T00:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultsToDemoAndAutoSync() = runTest {
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(DataSourceMode.DEMO, viewModel.uiState.value.settings.dataSourceMode)
        assertTrue(viewModel.uiState.value.settings.autoSyncEnabled)
        assertEquals(
            NotificationPromptState.NOT_ASKED,
            viewModel.uiState.value.settings.notificationPromptState
        )
    }

    @Test
    fun switchingMode_observesTheMetadataOfThatSource() = runTest {
        val repository = FakeSettingsRepository()
        repository.demoMetadata.value = syncMetadata("demo", now.minusSeconds(60))
        repository.officialMetadata.value = syncMetadata("official", now)
        val settingsStore = FakeAppSettingsStore()
        val viewModel = createViewModel(settingsStore = settingsStore, repository = repository)
        advanceUntilIdle()

        assertEquals(now.minusSeconds(60), viewModel.uiState.value.lastSuccessfulSyncAt)

        settingsStore.setDataSourceMode(DataSourceMode.OFFICIAL)
        advanceUntilIdle()

        assertEquals(now, viewModel.uiState.value.lastSuccessfulSyncAt)
        assertEquals(DataSourceMode.OFFICIAL, viewModel.uiState.value.settings.dataSourceMode)
    }

    @Test
    fun syncNowOffline_setsOfflineHintWithoutCallingSync() = runTest {
        val syncService = FakeSettingsSyncService()
        val viewModel = createViewModel(
            syncService = syncService,
            networkMonitor = FakeSettingsNetworkMonitor(isOnline = false)
        )
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.operationError)
        assertEquals(0, syncService.calls)
    }

    @Test
    fun localReadFailure_canBeRetried() = runTest {
        val repository = FakeSettingsRepository()
        repository.metadataFailuresRemaining = 1
        val viewModel = createViewModel(repository = repository)
        advanceUntilIdle()

        assertEquals("disk I/O error", viewModel.uiState.value.localReadError)

        viewModel.retryLocalRead()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.localReadError)
    }

    @Test
    fun autoSyncAndPromptPreference_arePersisted() = runTest {
        val settingsStore = FakeAppSettingsStore()
        val viewModel = createViewModel(settingsStore = settingsStore)
        advanceUntilIdle()

        viewModel.setAutoSyncEnabled(false)
        viewModel.setNotificationPromptState(NotificationPromptState.PROMPTED)
        advanceUntilIdle()

        assertEquals(false, settingsStore.settings.value.autoSyncEnabled)
        assertEquals(
            NotificationPromptState.PROMPTED,
            settingsStore.settings.value.notificationPromptState
        )
    }

    private fun createViewModel(
        settingsStore: FakeAppSettingsStore = FakeAppSettingsStore(),
        repository: FakeSettingsRepository = FakeSettingsRepository(),
        syncService: FakeSettingsSyncService = FakeSettingsSyncService(),
        networkMonitor: FakeSettingsNetworkMonitor = FakeSettingsNetworkMonitor()
    ) = SettingsViewModel(
        settingsStore = settingsStore,
        repository = repository,
        syncService = syncService,
        networkMonitor = networkMonitor,
        demoSourceId = "demo",
        officialSourceId = "official-unconfigured",
        officialAdapterConfigured = false
    )

    private fun syncMetadata(sourceId: String, lastSuccess: Instant?) = SyncMetadata(
        sourceId = sourceId,
        status = SyncStatus.SUCCESS,
        baselineEstablished = true,
        lastStartedAt = lastSuccess,
        lastFinishedAt = lastSuccess,
        lastSuccessfulSyncAt = lastSuccess,
        errorMessage = null
    )
}

private class FakeAppSettingsStore(initial: AppSettings = AppSettings()) : AppSettingsStore {
    override val settings = MutableStateFlow(initial)

    override suspend fun setDataSourceMode(mode: DataSourceMode) {
        settings.value = settings.value.copy(dataSourceMode = mode)
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(autoSyncEnabled = enabled)
    }

    override suspend fun setNotificationPromptState(state: NotificationPromptState) {
        settings.value = settings.value.copy(notificationPromptState = state)
    }
}

private class FakeSettingsNetworkMonitor(isOnline: Boolean = true) : NetworkMonitor {
    override val isOnline: Flow<Boolean> = flowOf(isOnline)
}

private class FakeSettingsSyncService : SyncService {
    var calls = 0
    var result: SyncResult = SyncResult.Success(
        baselineCreated = false,
        snapshotsAppended = 0,
        notificationEventsCreated = 0
    )

    override suspend fun sync(): SyncResult {
        calls += 1
        return result
    }
}

private class FakeSettingsRepository : RadarRepository {
    val demoMetadata = MutableStateFlow<SyncMetadata?>(null)
    val officialMetadata = MutableStateFlow<SyncMetadata?>(null)
    var metadataFailuresRemaining = 0

    override fun observeSyncMetadata(sourceId: String): Flow<SyncMetadata?> = flow {
        if (metadataFailuresRemaining > 0) {
            metadataFailuresRemaining -= 1
            error("disk I/O error")
        }
        emitAll(
            if (sourceId == "demo") demoMetadata else officialMetadata
        )
    }

    override fun observeHomeProducts(now: Instant): Flow<List<HomeProduct>> = flowOf(emptyList())
    override fun observeProductDetail(productId: String): Flow<ProductDetail?> = flowOf(null)
    override fun observePriceHistory(key: ComparisonKey): Flow<List<PriceSnapshot>> = flowOf(emptyList())
    override fun observeSubscribedProductIds(): Flow<List<String>> = flowOf(emptyList())
    override fun observeClickStatistics(): Flow<List<ProductClickCount>> = flowOf(emptyList())
    override suspend fun getActiveOffers(productId: String, now: Instant): List<Offer> = emptyList()
    override suspend fun getOfficialOfferUrl(productId: String, offerId: String): String? = null
    override suspend fun applySync(batch: SyncBatch) = Unit
    override suspend fun setSubscribed(productId: String, subscribed: Boolean, at: Instant) = Unit
    override suspend fun subscribeAll(at: Instant) = Unit
    override suspend fun isSubscribed(productId: String): Boolean = false
    override suspend fun recordOfficialClick(productId: String, offerId: String, clickedAt: Instant) = Unit
    override suspend fun clearClickHistory() = Unit
    override suspend fun hasNotificationEvent(eventId: String): Boolean = false
}
