package com.aioffer.radar.data

import com.aioffer.radar.core.notification.NotificationDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotifyingSyncService(
    private val delegate: SyncService,
    private val notificationDispatcher: NotificationDispatcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SyncService {
    override suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        val result = delegate.sync()
        if (result !is SyncResult.Success) return@withContext result
        try {
            notificationDispatcher.dispatchPending()
            result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            SyncResult.Failure(error)
        }
    }
}
