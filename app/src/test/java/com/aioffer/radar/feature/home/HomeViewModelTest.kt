package com.aioffer.radar.feature.home

import com.aioffer.radar.core.datastore.AppSettingsStore
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.NotificationPromptState
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.core.model.ProductDetail
import com.aioffer.radar.core.model.SourceInfo
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.core.network.NetworkMonitor
import com.aioffer.radar.core.testing.testOffer
import com.aioffer.radar.data.RadarRepository
import com.aioffer.radar.data.SyncBatch
import com.aioffer.radar.data.SyncResult
import com.aioffer.radar.data.SyncService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialSync_showsContentAndDemoSource() = runTest {
        val repository = FakeRadarRepository()
        val syncService = FakeSyncService {
            repository.homeProducts.value = listOf(testHomeProduct())
            SyncResult.Success(true, 1, 0)
        }

        val viewModel = createViewModel(repository, syncService)
        advanceUntilIdle()

        assertEquals(HomeContentState.CONTENT, viewModel.uiState.value.contentState)
        assertEquals(1, viewModel.uiState.value.products.size)
        assertTrue(viewModel.uiState.value.isDemoMode)
        assertEquals(DataSourceMode.DEMO, viewModel.uiState.value.dataSourceMode)
        assertFalse(viewModel.uiState.value.officialAdapterConfigured)
        assertEquals(1, syncService.calls)
    }

    @Test
    fun successfulSyncWithoutProducts_showsEmpty() = runTest {
        val viewModel = createViewModel(FakeRadarRepository(), FakeSyncService())
        advanceUntilIdle()

        assertEquals(HomeContentState.EMPTY, viewModel.uiState.value.contentState)
    }

    @Test
    fun failedInitialSync_showsError() = runTest {
        val syncService = FakeSyncService().apply {
            result = SyncResult.Failure(IllegalStateException("failed"))
        }
        val viewModel = createViewModel(FakeRadarRepository(), syncService)
        advanceUntilIdle()

        assertEquals(HomeContentState.ERROR, viewModel.uiState.value.contentState)
        assertEquals(HomeError.SYNC, viewModel.uiState.value.error)
    }

    @Test
    fun switchingDataSourceMode_clearsStaleSyncError() = runTest {
        val settingsStore = FakeAppSettingsStore()
        val repository = FakeRadarRepository(listOf(testHomeProduct()))
        val syncService = FakeSyncService().apply {
            result = SyncResult.Failure(IllegalStateException("failed"))
        }
        val viewModel = createViewModel(repository, syncService, settingsStore = settingsStore)
        advanceUntilIdle()
        assertEquals(HomeError.SYNC, viewModel.uiState.value.error)

        // SettingsViewModel changes the DataStore value; HomeViewModel must
        // not keep the failure from the previous source.
        settingsStore.setDataSourceMode(DataSourceMode.OFFICIAL)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(DataSourceMode.OFFICIAL, viewModel.uiState.value.dataSourceMode)
        assertEquals(HomeContentState.EMPTY, viewModel.uiState.value.contentState)

        settingsStore.setDataSourceMode(DataSourceMode.DEMO)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(HomeContentState.CONTENT, viewModel.uiState.value.contentState)
        assertTrue(viewModel.uiState.value.products.isNotEmpty())
    }

    @Test
    fun offlineWithCache_keepsContentAndSkipsSync() = runTest {
        val repository = FakeRadarRepository(listOf(testHomeProduct()))
        val networkMonitor = FakeNetworkMonitor(false)
        val syncService = FakeSyncService()
        val viewModel = createViewModel(repository, syncService, networkMonitor)
        advanceUntilIdle()

        assertEquals(HomeContentState.CONTENT, viewModel.uiState.value.contentState)
        assertTrue(viewModel.uiState.value.isOffline)
        assertTrue(viewModel.uiState.value.isShowingCachedData)
        assertEquals(0, syncService.calls)
    }

    @Test
    fun syncFailure_keepsCachedProducts() = runTest {
        val repository = FakeRadarRepository(listOf(testHomeProduct()))
        val syncService = FakeSyncService().apply {
            result = SyncResult.Failure(IllegalStateException("failed"))
        }
        val viewModel = createViewModel(repository, syncService)
        advanceUntilIdle()

        assertEquals(HomeContentState.CONTENT, viewModel.uiState.value.contentState)
        assertEquals(1, viewModel.uiState.value.products.size)
        assertEquals(HomeError.SYNC, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isShowingCachedData)
    }

    @Test
    fun manualRefresh_exposesSyncingAndPreventsDuplicateSync() = runTest {
        val syncService = FakeSyncService().apply { suspendSync = true }
        val viewModel = createViewModel(FakeRadarRepository(), syncService)
        runCurrent()

        assertTrue(viewModel.uiState.value.isSyncing)
        viewModel.refresh()
        runCurrent()
        assertEquals(1, syncService.calls)

        syncService.completeNextSync()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    @Test
    fun subscription_isImmediateAndSurvivesViewModelRecreation() = runTest {
        val repository = FakeRadarRepository(listOf(testHomeProduct()))
        repository.pauseSubscriptionWrites = true
        val viewModel = createViewModel(repository, FakeSyncService())
        advanceUntilIdle()

        viewModel.setSubscribed("kimi", true)
        runCurrent()
        assertTrue(viewModel.uiState.value.products.single().isSubscribed)
        assertFalse(repository.persistedSubscriptions.contains("kimi"))

        repository.resumeSubscriptionWrites()
        advanceUntilIdle()
        assertTrue(repository.persistedSubscriptions.contains("kimi"))

        val recreated = createViewModel(repository, FakeSyncService())
        advanceUntilIdle()
        assertTrue(recreated.uiState.value.products.single().isSubscribed)
    }

    @Test
    fun unsubscribe_updatesImmediately() = runTest {
        val repository = FakeRadarRepository(listOf(testHomeProduct(isSubscribed = true)))
        repository.persistedSubscriptions += "kimi"
        val viewModel = createViewModel(repository, FakeSyncService())
        advanceUntilIdle()

        viewModel.setSubscribed("kimi", false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.products.single().isSubscribed)
        assertFalse(repository.persistedSubscriptions.contains("kimi"))
    }

    @Test
    fun failedSubscription_rollsBackAndShowsError() = runTest {
        val repository = FakeRadarRepository(listOf(testHomeProduct())).apply {
            failSubscriptionWrites = true
        }
        val viewModel = createViewModel(repository, FakeSyncService())
        advanceUntilIdle()

        viewModel.setSubscribed("kimi", true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.products.single().isSubscribed)
        assertEquals(HomeError.SUBSCRIPTION, viewModel.uiState.value.error)
    }

    @Test
    fun subscribeAll_marksEveryProductImmediately() = runTest {
        val repository = FakeRadarRepository(
            listOf(
                testHomeProduct(),
                testHomeProduct(id = "deepseek", isSubscribed = true)
            )
        )
        repository.pauseSubscriptionWrites = true
        val viewModel = createViewModel(repository, FakeSyncService())
        advanceUntilIdle()

        viewModel.subscribeAll()
        runCurrent()
        assertTrue(viewModel.uiState.value.products.all { it.isSubscribed })

        repository.resumeSubscriptionWrites()
        advanceUntilIdle()
        assertEquals(setOf("kimi", "deepseek"), repository.persistedSubscriptions)
    }

    @Test
    fun officialMode_hidesDemoProductsAndShowsOfficialSource() = runTest {
        val settingsStore = FakeAppSettingsStore(
            AppSettings(dataSourceMode = DataSourceMode.OFFICIAL)
        )
        val repository = FakeRadarRepository(listOf(testHomeProduct()))
        repository.officialMetadata.value = syncMetadata(
            sourceId = "official-unconfigured",
            status = SyncStatus.SUCCESS,
            lastSuccessfulSyncAt = clock.instant()
        )
        val viewModel = createViewModel(
            repository = repository,
            syncService = FakeSyncService(),
            settingsStore = settingsStore
        )
        advanceUntilIdle()

        assertEquals(HomeContentState.EMPTY, viewModel.uiState.value.contentState)
        assertFalse(viewModel.uiState.value.isDemoMode)
        assertEquals(DataSourceMode.OFFICIAL, viewModel.uiState.value.dataSourceMode)
        assertTrue(viewModel.uiState.value.products.isEmpty())
        assertEquals(clock.instant(), viewModel.uiState.value.lastSuccessfulSyncAt)
    }

    @Test
    fun switchingDataSource_neverRelabelsStoredDemoHistory() = runTest {
        val settingsStore = FakeAppSettingsStore()
        val repository = FakeRadarRepository(listOf(testHomeProduct()))
        val viewModel = createViewModel(
            repository = repository,
            syncService = FakeSyncService(),
            settingsStore = settingsStore
        )
        advanceUntilIdle()
        assertEquals(HomeContentState.CONTENT, viewModel.uiState.value.contentState)
        assertEquals(
            DataSourceType.DEMO,
            repository.homeProducts.value.single().activeOffers.single().source.type
        )

        viewModel.setDataSourceMode(DataSourceMode.OFFICIAL)
        advanceUntilIdle()
        assertEquals(HomeContentState.EMPTY, viewModel.uiState.value.contentState)
        assertTrue(viewModel.uiState.value.products.isEmpty())
        // Switching the display range must not mutate the stored source type.
        assertEquals(
            DataSourceType.DEMO,
            repository.homeProducts.value.single().activeOffers.single().source.type
        )

        viewModel.setDataSourceMode(DataSourceMode.DEMO)
        advanceUntilIdle()
        assertEquals(HomeContentState.CONTENT, viewModel.uiState.value.contentState)
        assertEquals("demo", viewModel.uiState.value.products.single().activeOffers.single().source.id)
    }

    @Test
    fun officialOffersAreOnlyVisibleInOfficialMode() = runTest {
        val settingsStore = FakeAppSettingsStore()
        val repository = FakeRadarRepository(
            listOf(
                testHomeProduct(id = "kimi"),
                testHomeProduct(id = "deepseek", sourceType = DataSourceType.OFFICIAL_API)
            )
        )
        val viewModel = createViewModel(
            repository = repository,
            syncService = FakeSyncService(),
            settingsStore = settingsStore
        )
        advanceUntilIdle()
        assertEquals("kimi", viewModel.uiState.value.products.single().product.id)

        viewModel.setDataSourceMode(DataSourceMode.OFFICIAL)
        advanceUntilIdle()
        assertEquals("deepseek", viewModel.uiState.value.products.single().product.id)
        assertEquals(
            DataSourceType.OFFICIAL_API,
            viewModel.uiState.value.products.single().activeOffers.single().source.type
        )
    }

    @Test
    fun lastSuccessfulSyncAndFailureReason_areExposed() = runTest {
        val successfulAt = clock.instant().minusSeconds(120)
        val repository = FakeRadarRepository(listOf(testHomeProduct()))
        repository.metadata.value = syncMetadata(
            sourceId = "demo",
            status = SyncStatus.FAILED,
            lastSuccessfulSyncAt = successfulAt,
            errorMessage = "demo network failure"
        )
        val syncService = FakeSyncService().apply {
            result = SyncResult.Failure(IllegalStateException("failed"))
        }
        val viewModel = createViewModel(repository, syncService)
        advanceUntilIdle()

        assertEquals(successfulAt, viewModel.uiState.value.lastSuccessfulSyncAt)
        assertEquals("demo network failure", viewModel.uiState.value.lastSyncFailureReason)
        assertEquals(HomeError.SYNC, viewModel.uiState.value.error)
    }

    @Test
    fun databaseReadFailure_isRecoverableThroughRetry() = runTest {
        val repository = FakeRadarRepository(listOf(testHomeProduct())).apply {
            failHomeProductsRead = true
        }
        val viewModel = createViewModel(repository, FakeSyncService())
        advanceUntilIdle()

        assertEquals(HomeContentState.ERROR, viewModel.uiState.value.contentState)
        assertEquals(HomeError.DATABASE, viewModel.uiState.value.error)
        assertNotNull(viewModel.uiState.value.databaseErrorMessage)

        repository.failHomeProductsRead = false
        viewModel.retryAfterDatabaseError()
        advanceUntilIdle()

        assertEquals(HomeContentState.CONTENT, viewModel.uiState.value.contentState)
        assertNull(viewModel.uiState.value.error)
        assertEquals(1, viewModel.uiState.value.products.size)
    }

    @Test
    fun notificationPromptDismissal_isPersisted() = runTest {
        val settingsStore = FakeAppSettingsStore()
        val viewModel = createViewModel(
            repository = FakeRadarRepository(),
            syncService = FakeSyncService(),
            settingsStore = settingsStore
        )
        advanceUntilIdle()
        assertEquals(
            NotificationPromptState.NOT_ASKED,
            viewModel.uiState.value.notificationPromptState
        )

        viewModel.dismissNotificationPrompt()
        advanceUntilIdle()

        assertEquals(
            NotificationPromptState.PROMPTED,
            settingsStore.settings.value.notificationPromptState
        )
        assertEquals(
            NotificationPromptState.PROMPTED,
            viewModel.uiState.value.notificationPromptState
        )
    }

    @Test
    fun offerExpiresWhileScreenIsOpen_disappearsFromCurrentOffers() = runTest {
        val endsAt = clock.instant().plusSeconds(60)
        val ticker = MutableStateFlow(clock.instant())
        val repository = FakeRadarRepository(
            listOf(testHomeProduct(offerEndsAt = endsAt))
        )
        val viewModel = createViewModel(
            repository = repository,
            syncService = FakeSyncService(),
            nowTicker = ticker
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.products.single().activeOffers.size)

        ticker.value = endsAt.plusSeconds(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.products.single().activeOffers.isEmpty())
    }

    private fun createViewModel(
        repository: FakeRadarRepository,
        syncService: FakeSyncService,
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(),
        settingsStore: FakeAppSettingsStore = FakeAppSettingsStore(),
        officialAdapterConfigured: Boolean = false,
        nowTicker: Flow<Instant> = flowOf(clock.instant())
    ) = HomeViewModel(
        repository = repository,
        syncService = syncService,
        networkMonitor = networkMonitor,
        clock = clock,
        settingsStore = settingsStore,
        demoSourceId = "demo",
        officialSourceId = "official-unconfigured",
        officialAdapterConfigured = officialAdapterConfigured,
        nowTicker = nowTicker
    )

    private fun syncMetadata(
        sourceId: String,
        status: SyncStatus,
        lastSuccessfulSyncAt: Instant? = null,
        errorMessage: String? = null
    ) = SyncMetadata(
        sourceId = sourceId,
        status = status,
        baselineEstablished = lastSuccessfulSyncAt != null,
        lastStartedAt = lastSuccessfulSyncAt,
        lastFinishedAt = lastSuccessfulSyncAt,
        lastSuccessfulSyncAt = lastSuccessfulSyncAt,
        errorMessage = errorMessage
    )

    private fun testHomeProduct(
        id: String = "kimi",
        isSubscribed: Boolean = false,
        offerEndsAt: Instant? = null,
        sourceType: DataSourceType = DataSourceType.DEMO
    ): HomeProduct {
        val key = ComparisonKey(
            productId = id,
            planId = "$id-standard",
            region = com.aioffer.radar.core.model.Region.MAINLAND_CHINA,
            currency = com.aioffer.radar.core.model.Currency.CNY,
            billingPeriod = com.aioffer.radar.core.model.BillingPeriod.MONTHLY,
            taxIncluded = true
        )
        val source = SourceInfo(
            id = if (sourceType == DataSourceType.DEMO) "demo" else "official-$id",
            type = sourceType,
            sourceUrl = if (sourceType == DataSourceType.DEMO) null else "https://example.com/$id",
            titleOrSummary = if (sourceType == DataSourceType.DEMO) {
                "Demo data"
            } else {
                "Official data"
            },
            fetchedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val offer = testOffer(key = key, endsAt = offerEndsAt).copy(source = source)
        return HomeProduct(
            product = Product(
                id = id,
                name = id.uppercase(),
                isDemo = sourceType == DataSourceType.DEMO
            ),
            activeOffers = listOf(offer),
            isSubscribed = isSubscribed,
            historicalLowestOfferIds = setOf(offer.id)
        )
    }
}

private class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(initiallyOnline)
    override val isOnline: Flow<Boolean> = online
}

private class FakeAppSettingsStore(initial: AppSettings = AppSettings()) : AppSettingsStore {
    override val settings = MutableStateFlow(initial)

    override suspend fun setDataSourceMode(mode: DataSourceMode) {
        settings.update { it.copy(dataSourceMode = mode) }
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        settings.update { it.copy(autoSyncEnabled = enabled) }
    }

    override suspend fun setNotificationPromptState(state: NotificationPromptState) {
        settings.update { it.copy(notificationPromptState = state) }
    }
}

private class FakeSyncService(
    private val handler: (suspend () -> SyncResult)? = null
) : SyncService {
    var calls = 0
    var result: SyncResult = SyncResult.Success(false, 0, 0)
    var suspendSync = false
    private var activeResult: kotlinx.coroutines.CompletableDeferred<SyncResult>? = null

    override suspend fun sync(): SyncResult {
        calls++
        handler?.let { return it() }
        if (!suspendSync) return result
        if (activeResult == null) {
            activeResult = kotlinx.coroutines.CompletableDeferred()
        }
        return activeResult!!.await()
    }

    fun completeNextSync() {
        activeResult?.complete(result)
    }
}

private class FakeRadarRepository(
    initialProducts: List<HomeProduct> = emptyList()
) : RadarRepository {
    val homeProducts = MutableStateFlow(initialProducts)
    val metadata = MutableStateFlow<SyncMetadata?>(null)
    val officialMetadata = MutableStateFlow<SyncMetadata?>(null)
    var failHomeProductsRead = false
    val persistedSubscriptions = mutableSetOf<String>()
    var pauseSubscriptionWrites = false
    var failSubscriptionWrites = false
    private var pendingWrites = 0
    private var resume: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun observeHomeProducts(now: Instant): Flow<List<HomeProduct>> {
        if (failHomeProductsRead) {
            return flow { error("Local database is unavailable") }
        }
        return homeProducts.map { products ->
            products.map { product ->
                product.copy(
                    activeOffers = product.activeOffers.filter { offer ->
                        offer.endsAt?.let { end -> now.isBefore(end) } ?: true
                    }
                )
            }
        }
    }

    override fun observeProductDetail(productId: String): Flow<ProductDetail?> =
        MutableStateFlow(null)

    override fun observePriceHistory(key: ComparisonKey): Flow<List<PriceSnapshot>> =
        MutableStateFlow(emptyList())

    override fun observeSubscribedProductIds(): Flow<List<String>> =
        MutableStateFlow(persistedSubscriptions.toList())

    override fun observeSyncMetadata(sourceId: String): Flow<SyncMetadata?> =
        if (sourceId == "demo") metadata else officialMetadata

    override fun observeClickStatistics(): Flow<List<ProductClickCount>> =
        MutableStateFlow(emptyList())

    override suspend fun getActiveOffers(productId: String, now: Instant): List<Offer> = emptyList()

    override suspend fun getOfficialOfferUrl(productId: String, offerId: String): String? = null

    override suspend fun applySync(batch: SyncBatch) = Unit

    override suspend fun setSubscribed(productId: String, subscribed: Boolean, at: Instant) {
        awaitIfPaused()
        if (failSubscriptionWrites) error("Subscription failed")
        if (subscribed) persistedSubscriptions += productId else persistedSubscriptions -= productId
        homeProducts.value = homeProducts.value.map { product ->
            if (product.product.id == productId) product.copy(isSubscribed = subscribed) else product
        }
    }

    override suspend fun subscribeAll(at: Instant) {
        awaitIfPaused()
        if (failSubscriptionWrites) error("Subscription failed")
        persistedSubscriptions += homeProducts.value.map { it.product.id }
        homeProducts.value = homeProducts.value.map { it.copy(isSubscribed = true) }
    }

    override suspend fun isSubscribed(productId: String): Boolean =
        productId in persistedSubscriptions

    override suspend fun recordOfficialClick(productId: String, offerId: String, clickedAt: Instant) = Unit

    override suspend fun clearClickHistory() = Unit

    override suspend fun hasNotificationEvent(eventId: String): Boolean = false

    fun resumeSubscriptionWrites() {
        resume?.complete(Unit)
    }

    private suspend fun awaitIfPaused() {
        if (!pauseSubscriptionWrites) return
        pendingWrites++
        if (resume == null) resume = kotlinx.coroutines.CompletableDeferred()
        resume!!.await()
        pendingWrites--
        if (pendingWrites == 0) {
            pauseSubscriptionWrites = false
            resume = null
        }
    }
}
