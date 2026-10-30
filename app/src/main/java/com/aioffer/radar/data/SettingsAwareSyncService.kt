package com.aioffer.radar.data

import com.aioffer.radar.core.datastore.AppSettingsStore
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Routes a sync request to the adapter that belongs to the currently selected
 * data source.
 *
 * This is the boundary that prevents demo payloads from being synchronised
 * under the official source ID (and vice versa) after a mode switch. Each
 * delegate has its own fixed [RemoteDataSource.sourceId]; the selected mode
 * only decides which delegate runs.
 */
class SettingsAwareSyncService(
    private val settingsStore: AppSettingsStore,
    private val demoSyncService: SyncService,
    private val officialSyncService: SyncService
) : SyncService {
    override suspend fun sync(): SyncResult {
        val mode = try {
            settingsStore.settings
                .catch { emit(AppSettings()) }
                .first()
                .dataSourceMode
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // If preferences cannot be read, fall back to the explicitly
            // labelled demo source rather than claiming official freshness.
            DataSourceMode.DEMO
        }
        return when (mode) {
            DataSourceMode.DEMO -> demoSyncService.sync()
            DataSourceMode.OFFICIAL -> officialSyncService.sync()
        }
    }
}
