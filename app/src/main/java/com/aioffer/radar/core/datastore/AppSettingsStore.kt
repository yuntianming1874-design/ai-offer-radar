package com.aioffer.radar.core.datastore

import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.NotificationPromptState
import kotlinx.coroutines.flow.Flow

/**
 * Persisted, observable app preferences.
 *
 * Implementations must keep all disk access off the main thread. The
 * DataStore-backed implementation relies on Preferences DataStore, which
 * performs reads and edits on its own IO dispatcher.
 */
interface AppSettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setDataSourceMode(mode: DataSourceMode)

    suspend fun setAutoSyncEnabled(enabled: Boolean)

    suspend fun setNotificationPromptState(state: NotificationPromptState)
}
