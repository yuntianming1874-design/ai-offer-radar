package com.aioffer.radar.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.NotificationPromptState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val APP_SETTINGS_PREFERENCES_NAME = "app_settings"

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = APP_SETTINGS_PREFERENCES_NAME
)

private object SettingsKeys {
    val dataSourceMode = stringPreferencesKey("data_source_mode")
    val autoSyncEnabled = booleanPreferencesKey("auto_sync_enabled")
    val notificationPromptState = stringPreferencesKey("notification_prompt_state")
}

fun createAppSettingsStore(context: Context): AppSettingsStore =
    DataStoreAppSettingsStore(context.applicationContext.appSettingsDataStore)

/**
 * DataStore-backed settings. DataStore owns its IO dispatcher, so reads and
 * writes never run on the main thread even when callers invoke the suspend
 * functions from a UI coroutine scope.
 */
class DataStoreAppSettingsStore(
    private val dataStore: DataStore<Preferences>
) : AppSettingsStore {
    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                // A corrupt/unreadable preferences file must not crash the app.
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                dataSourceMode = preferences[SettingsKeys.dataSourceMode].toDataSourceMode(),
                autoSyncEnabled = preferences[SettingsKeys.autoSyncEnabled] ?: true,
                notificationPromptState = preferences[SettingsKeys.notificationPromptState]
                    .toNotificationPromptState()
            )
        }

    override suspend fun setDataSourceMode(mode: DataSourceMode) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.dataSourceMode] = mode.name
        }
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.autoSyncEnabled] = enabled
        }
    }

    override suspend fun setNotificationPromptState(state: NotificationPromptState) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.notificationPromptState] = state.name
        }
    }

    private fun String?.toDataSourceMode(): DataSourceMode =
        DataSourceMode.entries.firstOrNull { it.name == this } ?: DataSourceMode.DEMO

    private fun String?.toNotificationPromptState(): NotificationPromptState =
        NotificationPromptState.entries.firstOrNull { it.name == this }
            ?: NotificationPromptState.NOT_ASKED
}
