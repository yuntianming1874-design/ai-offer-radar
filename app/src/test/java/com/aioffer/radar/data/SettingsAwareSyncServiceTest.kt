package com.aioffer.radar.data

import com.aioffer.radar.core.datastore.AppSettingsStore
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.NotificationPromptState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAwareSyncServiceTest {
    @Test
    fun demoMode_usesOnlyTheDemoSyncService() = runTest {
        val demo = RecordingSyncService()
        val official = RecordingSyncService()
        val service = SettingsAwareSyncService(
            settingsStore = FakeSettingsStore(AppSettings(dataSourceMode = DataSourceMode.DEMO)),
            demoSyncService = demo,
            officialSyncService = official
        )

        service.sync()

        assertEquals(1, demo.calls)
        assertEquals(0, official.calls)
    }

    @Test
    fun officialMode_usesOnlyTheOfficialSyncService() = runTest {
        val demo = RecordingSyncService()
        val official = RecordingSyncService()
        val service = SettingsAwareSyncService(
            settingsStore = FakeSettingsStore(AppSettings(dataSourceMode = DataSourceMode.OFFICIAL)),
            demoSyncService = demo,
            officialSyncService = official
        )

        service.sync()

        assertEquals(0, demo.calls)
        assertEquals(1, official.calls)
    }

    @Test
    fun modeIsReadOnEverySync_notCapturedAtConstruction() = runTest {
        val settingsStore = FakeSettingsStore(AppSettings(dataSourceMode = DataSourceMode.DEMO))
        val demo = RecordingSyncService()
        val official = RecordingSyncService()
        val service = SettingsAwareSyncService(settingsStore, demo, official)

        service.sync()
        settingsStore.setDataSourceMode(DataSourceMode.OFFICIAL)
        service.sync()

        assertEquals(1, demo.calls)
        assertEquals(1, official.calls)
    }
}

private class RecordingSyncService : SyncService {
    var calls = 0
    override suspend fun sync(): SyncResult {
        calls++
        return SyncResult.Success(
            baselineCreated = false,
            snapshotsAppended = 0,
            notificationEventsCreated = 0
        )
    }
}

private class FakeSettingsStore(initial: AppSettings) : AppSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setDataSourceMode(mode: DataSourceMode) {
        state.value = state.value.copy(dataSourceMode = mode)
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        state.value = state.value.copy(autoSyncEnabled = enabled)
    }

    override suspend fun setNotificationPromptState(state: NotificationPromptState) {
        this.state.value = this.state.value.copy(notificationPromptState = state)
    }
}
