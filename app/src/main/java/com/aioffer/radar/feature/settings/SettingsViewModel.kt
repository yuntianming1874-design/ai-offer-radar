package com.aioffer.radar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioffer.radar.core.datastore.AppSettingsStore
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.NotificationPromptState
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.core.network.NetworkMonitor
import com.aioffer.radar.data.RadarRepository
import com.aioffer.radar.data.SyncResult
import com.aioffer.radar.data.SyncService
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val officialAdapterConfigured: Boolean = false,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSuccessfulSyncAt: Instant? = null,
    val lastSyncStatus: SyncStatus? = null,
    val lastSyncFailureReason: String? = null,
    val localReadError: String? = null,
    val operationError: String? = null
)

class SettingsViewModel(
    private val settingsStore: AppSettingsStore,
    private val repository: RadarRepository,
    private val syncService: SyncService,
    private val networkMonitor: NetworkMonitor,
    private val demoSourceId: String,
    private val officialSourceId: String,
    private val officialAdapterConfigured: Boolean
) : ViewModel() {
    private val retrySignal = MutableStateFlow(0)
    private val syncing = MutableStateFlow(false)
    private val operationError = MutableStateFlow<String?>(null)
    private val localReadError = MutableStateFlow<String?>(null)

    private val syncState = combine(syncing, operationError) { isSyncing, error ->
        isSyncing to error
    }

    private val settings = settingsStore.settings
        .catch { emit(AppSettings()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val metadata = settings
        .map { it.dataSourceMode }
        .distinctUntilChanged()
        .flatMapLatest { mode ->
            retrySignal.flatMapLatest {
                val sourceId = when (mode) {
                    DataSourceMode.DEMO -> demoSourceId
                    DataSourceMode.OFFICIAL -> officialSourceId
                }
                repository.observeSyncMetadata(sourceId)
                    .onEach { localReadError.value = null }
                    .catch { error ->
                        localReadError.value = error.userMessage()
                        emit(null)
                    }
            }
        }

    val uiState: StateFlow<SettingsUiState> = combine(
        settings,
        metadata,
        networkMonitor.isOnline,
        localReadError,
        syncState
    ) { appSettings, syncMetadata, isOnline, readError, (isSyncing, operationError) ->
        SettingsUiState(
            settings = appSettings,
            officialAdapterConfigured = officialAdapterConfigured,
            isOnline = isOnline,
            isSyncing = isSyncing,
            lastSuccessfulSyncAt = syncMetadata?.lastSuccessfulSyncAt,
            lastSyncStatus = syncMetadata?.status,
            lastSyncFailureReason = syncMetadata
                ?.takeIf { it.status == SyncStatus.FAILED }
                ?.errorMessage,
            localReadError = readError,
            operationError = operationError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(officialAdapterConfigured = officialAdapterConfigured)
    )

    fun setDataSourceMode(mode: DataSourceMode) {
        if (uiState.value.settings.dataSourceMode == mode) return
        viewModelScope.launch {
            runCatching { settingsStore.setDataSourceMode(mode) }
                .onFailure { operationError.value = it.userMessage() }
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsStore.setAutoSyncEnabled(enabled) }
                .onFailure { operationError.value = it.userMessage() }
        }
    }

    fun setNotificationPromptState(state: NotificationPromptState) {
        viewModelScope.launch {
            runCatching { settingsStore.setNotificationPromptState(state) }
                .onFailure { operationError.value = it.userMessage() }
        }
    }

    fun syncNow() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            operationError.value = null
            try {
                if (!networkMonitor.isOnline.first()) {
                    operationError.value = "offline"
                    return@launch
                }
                when (val result = syncService.sync()) {
                    is SyncResult.Success -> Unit
                    is SyncResult.Failure -> operationError.value = result.error.userMessage()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                operationError.value = error.userMessage()
            } finally {
                syncing.value = false
            }
        }
    }

    /**
     * Recovery entry for a failed metadata read. Re-subscribing also lets the
     * screen refresh the displayed last-sync information.
     */
    fun retryLocalRead() {
        localReadError.value = null
        retrySignal.update { it + 1 }
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    companion object {
        fun factory(
            settingsStore: AppSettingsStore,
            repository: RadarRepository,
            syncService: SyncService,
            networkMonitor: NetworkMonitor,
            demoSourceId: String,
            officialSourceId: String,
            officialAdapterConfigured: Boolean
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    "Unknown ViewModel class: ${modelClass.name}"
                }
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(
                    settingsStore = settingsStore,
                    repository = repository,
                    syncService = syncService,
                    networkMonitor = networkMonitor,
                    demoSourceId = demoSourceId,
                    officialSourceId = officialSourceId,
                    officialAdapterConfigured = officialAdapterConfigured
                ) as T
            }
        }
    }
}
