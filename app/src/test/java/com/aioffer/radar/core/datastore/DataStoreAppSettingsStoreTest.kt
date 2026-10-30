package com.aioffer.radar.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.NotificationPromptState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAppSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaults_areSafeWhenNoSettingsWereEverSaved() = runBlocking {
        withDataStore("defaults") { dataStore ->
            val settings = DataStoreAppSettingsStore(dataStore).settings.first()

            assertEquals(AppSettings(), settings)
            assertEquals(DataSourceMode.DEMO, settings.dataSourceMode)
            assertEquals(true, settings.autoSyncEnabled)
            assertEquals(NotificationPromptState.NOT_ASKED, settings.notificationPromptState)
        }
    }

    @Test
    fun savedSettings_areReloadedByANewStoreInstance() = runBlocking {
        withDataStore("persisted") { dataStore ->
            val store = DataStoreAppSettingsStore(dataStore)
            store.setDataSourceMode(DataSourceMode.OFFICIAL)
            store.setAutoSyncEnabled(false)
            store.setNotificationPromptState(NotificationPromptState.PROMPTED)

            val reloaded = DataStoreAppSettingsStore(dataStore).settings.first()

            assertEquals(DataSourceMode.OFFICIAL, reloaded.dataSourceMode)
            assertEquals(false, reloaded.autoSyncEnabled)
            assertEquals(NotificationPromptState.PROMPTED, reloaded.notificationPromptState)
        }
    }

    @Test
    fun unknownStoredValues_fallBackWithoutCrashing() = runBlocking {
        withDataStore("unknown") { dataStore ->
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("data_source_mode")] = "LEGACY_MODE"
                preferences[stringPreferencesKey("notification_prompt_state")] = "SOMETHING_ELSE"
            }

            val settings = DataStoreAppSettingsStore(dataStore).settings.first()

            assertEquals(DataSourceMode.DEMO, settings.dataSourceMode)
            assertEquals(NotificationPromptState.NOT_ASKED, settings.notificationPromptState)
            assertEquals(true, settings.autoSyncEnabled)
        }
    }

    private suspend fun withDataStore(
        name: String,
        block: suspend (DataStore<Preferences>) -> Unit
    ) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val file = File(temporaryFolder.root, "$name.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
            block(dataStore)
        } finally {
            scope.cancel()
        }
    }
}
