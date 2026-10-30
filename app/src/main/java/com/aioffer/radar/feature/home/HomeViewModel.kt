package com.aioffer.radar.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioffer.radar.core.datastore.AppSettingsStore
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.NotificationPromptState
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.core.network.DemoRemoteDataSource
import com.aioffer.radar.core.network.DemoScenario
import com.aioffer.radar.core.network.NetworkMonitor
import com.aioffer.radar.data.RadarRepository
import com.aioffer.radar.data.SyncResult
import com.aioffer.radar.data.SyncService
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeContentState {
    LOADING,
    CONTENT,
    EMPTY,
    ERROR
}

enum class HomeError {
    OFFLINE,
    SYNC,
    SUBSCRIPTION,
    DATABASE
}

data class HomeUiState(
    val contentState: HomeContentState = HomeContentState.LOADING,
    val products: List<HomeProduct> = emptyList(),
    val isSyncing: Boolean = false,
    val isOffline: Boolean = false,
    val isDemoMode: Boolean = true,
    val dataSourceMode: DataSourceMode = DataSourceMode.DEMO,
    val officialAdapterConfigured: Boolean = false,
    val error: HomeError? = null,
    val lastSuccessfulSyncAt: Instant? = null,
    val lastSyncFailureReason: String? = null,
    val databaseErrorMessage: String? = null,
    val notificationPromptState: NotificationPromptState = NotificationPromptState.NOT_ASKED
) {
    val isShowingCachedData: Boolean
        get() = products.isNotEmpty() && (isOffline || error == HomeError.SYNC)

    val hasData: Boolean
        get() = products.isNotEmpty()
}

class HomeViewModel(
    private val repository: RadarRepository,
    private val syncService: SyncService,
    private val networkMonitor: NetworkMonitor,
    private val clock: Clock,
    private val settingsStore: AppSettingsStore,
    private val demoSourceId: String,
    private val officialSourceId: String,
    private val officialAdapterConfigured: Boolean = false,
    private val demoRemoteDataSource: DemoRemoteDataSource? = null,
    private val nowTicker: Flow<Instant> = flowOf(clock.instant())
) : ViewModel() {
    private val subscriptionOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val syncing = MutableStateFlow(false)
    private val operationError = MutableStateFlow<HomeError?>(null)
    private val refreshCompleted = MutableStateFlow(false)
    private val productsError = MutableStateFlow<String?>(null)
    private val metadataError = MutableStateFlow<String?>(null)
    private val retrySignal = MutableStateFlow(0)

    private val settings: StateFlow<AppSettings> = settingsStore.settings
        .catch { emit(AppSettings()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val products: Flow<List<HomeProduct>> =
        combine(nowTicker, subscriptionOverrides, settings) { now, overrides, appSettings ->
            Triple(now, overrides, appSettings.dataSourceMode)
        }.flatMapLatest { (now, overrides, mode) ->
            repository.observeHomeProducts(now)
                .onEach { productsError.value = null }
                .map { homeProducts ->
                    homeProducts.mapNotNull { homeProduct ->
                        homeProduct.visibleIn(mode, overrides)
                    }
                }
                .catch { error ->
                    productsError.value = error.userMessage()
                    emit(emptyList())
                }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val metadata: Flow<SyncMetadata?> = settings
        .map { it.dataSourceMode }
        .distinctUntilChanged()
        .flatMapLatest { mode ->
            repository.observeSyncMetadata(sourceIdFor(mode))
                .onEach { metadataError.value = null }
                .catch { error ->
                    metadataError.value = error.userMessage()
                    emit(null)
                }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val localData: Flow<LocalData> = retrySignal.flatMapLatest {
        combine(
            products,
            metadata,
            networkMonitor.isOnline,
            settings
        ) { homeProducts, syncMetadata, isOnline, appSettings ->
            LocalData(homeProducts, syncMetadata, isOnline, appSettings)
        }
    }

    private val databaseError = combine(productsError, metadataError) { productError, metadataError ->
        productError ?: metadataError
    }

    val uiState: StateFlow<HomeUiState> = combine(
        localData,
        syncing,
        operationError,
        refreshCompleted,
        databaseError
    ) { data, isSyncing, operationError, hasCompletedRefresh, databaseError ->
        val error = when {
            databaseError != null -> HomeError.DATABASE
            !data.isOnline -> HomeError.OFFLINE
            operationError != null -> operationError
            data.metadata?.status == SyncStatus.FAILED -> HomeError.SYNC
            else -> null
        }
        val contentState = when {
            data.products.isNotEmpty() -> HomeContentState.CONTENT
            error != null -> HomeContentState.ERROR
            isSyncing -> HomeContentState.LOADING
            hasCompletedRefresh -> HomeContentState.EMPTY
            else -> HomeContentState.LOADING
        }
        HomeUiState(
            contentState = contentState,
            products = data.products,
            isSyncing = isSyncing,
            isOffline = !data.isOnline,
            isDemoMode = data.settings.isDemoMode,
            dataSourceMode = data.settings.dataSourceMode,
            officialAdapterConfigured = officialAdapterConfigured,
            error = error,
            lastSuccessfulSyncAt = data.metadata?.lastSuccessfulSyncAt,
            lastSyncFailureReason = data.metadata
                ?.takeIf { it.status == SyncStatus.FAILED }
                ?.errorMessage,
            databaseErrorMessage = databaseError,
            notificationPromptState = data.settings.notificationPromptState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(
            isDemoMode = true,
            dataSourceMode = DataSourceMode.DEMO,
            officialAdapterConfigured = officialAdapterConfigured
        )
    )

    init {
        refresh()
        // The mode can also be changed from the settings screen, which has its
        // own ViewModel. Drop a stale sync/subscription error from the previous
        // mode so it cannot make the newly selected source look broken.
        viewModelScope.launch {
            settingsStore.settings
                .map { it.dataSourceMode }
                .distinctUntilChanged()
                .drop(1)
                .collect { operationError.value = null }
        }
    }

    fun refresh() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            operationError.value = null
            try {
                if (!networkMonitor.isOnline.first()) {
                    return@launch
                }
                when (syncService.sync()) {
                    is SyncResult.Success -> Unit
                    is SyncResult.Failure -> operationError.value = HomeError.SYNC
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                operationError.value = HomeError.SYNC
            } finally {
                syncing.value = false
                refreshCompleted.value = true
            }
        }
    }

    /**
     * Recovery entry for a failed local read. Re-subscribes the database flows
     * (a Flow that threw is completed and cannot recover by itself) and asks
     * for another sync when the network is available.
     */
    fun retryAfterDatabaseError() {
        productsError.value = null
        metadataError.value = null
        refreshCompleted.value = false
        retrySignal.update { it + 1 }
        refresh()
    }

    fun setSubscribed(productId: String, subscribed: Boolean) {
        subscriptionOverrides.update { it + (productId to subscribed) }
        viewModelScope.launch {
            try {
                repository.setSubscribed(productId, subscribed, clock.instant())
                if (operationError.value == HomeError.SUBSCRIPTION) {
                    operationError.value = null
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                subscriptionOverrides.update { it - productId }
                operationError.value = HomeError.SUBSCRIPTION
            }
        }
    }

    fun subscribeAll() {
        val productIds = uiState.value.products.map { it.product.id }
        if (productIds.isEmpty()) return
        subscriptionOverrides.update { overrides ->
            overrides + productIds.associateWith { true }
        }
        viewModelScope.launch {
            try {
                repository.subscribeAll(clock.instant())
                if (operationError.value == HomeError.SUBSCRIPTION) {
                    operationError.value = null
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                subscriptionOverrides.update { it - productIds.toSet() }
                operationError.value = HomeError.SUBSCRIPTION
            }
        }
    }

    fun selectDemoScenario(scenario: DemoScenario) {
        if (uiState.value.dataSourceMode != DataSourceMode.DEMO) return
        demoRemoteDataSource?.setNextScenario(scenario)
        refresh()
    }

    fun setDataSourceMode(mode: DataSourceMode) {
        viewModelScope.launch {
            try {
                settingsStore.setDataSourceMode(mode)
                operationError.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                operationError.value = HomeError.DATABASE
            }
        }
    }

    fun markNotificationPromptShown() {
        persistNotificationPromptState(NotificationPromptState.PROMPTED)
    }

    fun dismissNotificationPrompt() {
        persistNotificationPromptState(NotificationPromptState.PROMPTED)
    }

    private fun persistNotificationPromptState(state: NotificationPromptState) {
        if (uiState.value.notificationPromptState == state) return
        viewModelScope.launch {
            runCatching { settingsStore.setNotificationPromptState(state) }
        }
    }

    private fun sourceIdFor(mode: DataSourceMode): String = when (mode) {
        DataSourceMode.DEMO -> demoSourceId
        DataSourceMode.OFFICIAL -> officialSourceId
    }

    private fun HomeProduct.visibleIn(
        mode: DataSourceMode,
        overrides: Map<String, Boolean>
    ): HomeProduct? {
        val visibleOffers = activeOffers.filter { offer ->
            when (mode) {
                DataSourceMode.DEMO -> offer.source.type == DataSourceType.DEMO
                DataSourceMode.OFFICIAL -> offer.source.type != DataSourceType.DEMO
            }
        }
        if (visibleOffers.isEmpty()) {
            // Keep an offer-less product only when its own trust flag belongs to
            // the selected source range (this preserves the loading/expiry UI
            // without leaking demo product names into official mode).
            val belongsToMode = when (mode) {
                DataSourceMode.DEMO -> product.isDemo
                DataSourceMode.OFFICIAL -> !product.isDemo
            }
            if (!belongsToMode) return null
            return copy(
                activeOffers = emptyList(),
                historicalLowestOfferIds = emptySet(),
                isSubscribed = overrides[product.id] ?: isSubscribed
            )
        }
        val visibleIds = visibleOffers.mapTo(mutableSetOf()) { it.id }
        val overridden = overrides[product.id]?.let { subscribed ->
            copy(isSubscribed = subscribed)
        } ?: this
        return overridden.copy(
            activeOffers = visibleOffers,
            historicalLowestOfferIds = historicalLowestOfferIds intersect visibleIds
        )
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private data class LocalData(
        val products: List<HomeProduct>,
        val metadata: SyncMetadata?,
        val isOnline: Boolean,
        val settings: AppSettings
    )

    companion object {
        fun factory(
            repository: RadarRepository,
            syncService: SyncService,
            networkMonitor: NetworkMonitor,
            clock: Clock,
            settingsStore: AppSettingsStore,
            demoSourceId: String,
            officialSourceId: String,
            officialAdapterConfigured: Boolean = false,
            demoRemoteDataSource: DemoRemoteDataSource? = null,
            nowTicker: Flow<Instant> = flowOf(clock.instant())
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    "Unknown ViewModel class: ${modelClass.name}"
                }
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(
                    repository = repository,
                    syncService = syncService,
                    networkMonitor = networkMonitor,
                    clock = clock,
                    settingsStore = settingsStore,
                    demoSourceId = demoSourceId,
                    officialSourceId = officialSourceId,
                    officialAdapterConfigured = officialAdapterConfigured,
                    demoRemoteDataSource = demoRemoteDataSource,
                    nowTicker = nowTicker
                ) as T
            }
        }
    }
}
