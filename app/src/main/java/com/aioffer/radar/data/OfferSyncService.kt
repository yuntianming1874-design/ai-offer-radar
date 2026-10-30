package com.aioffer.radar.data

import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.core.network.RemoteDataSource
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface SyncResult {
    data class Success(
        val baselineCreated: Boolean,
        val snapshotsAppended: Int,
        val notificationEventsCreated: Int
    ) : SyncResult

    data class Failure(val error: Throwable) : SyncResult
}

interface SyncService {
    suspend fun sync(): SyncResult
}

class OfferSyncService(
    private val remoteDataSource: RemoteDataSource,
    private val database: RadarDatabase,
    private val repository: RadarRepository,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SyncService {
    private val syncMutex = Mutex()

    override suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            val startedAt = clock.instant()
            try {
                val payload = remoteDataSource.fetch()
                val normalized = RemotePayloadNormalizer.normalize(payload)
                if (normalized.source.id != remoteDataSource.sourceId) {
                    throw RemotePayloadNormalizationException(
                        "Payload source ID does not match the remote data source"
                    )
                }
                synchronize(normalized, startedAt)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                recordFailure(startedAt, error)
                SyncResult.Failure(error)
            }
        }
    }

    private suspend fun synchronize(normalized: NormalizedPayload, startedAt: Instant): SyncResult {
        val metadata = database.syncMetadataDao().getBySourceId(normalized.source.id)
        val hadBaseline = metadata?.baselineEstablished == true &&
            metadata.lastSuccessfulSyncAtEpochMillis != null
        val sourceEntity = database.sourceInfoDao().getById(normalized.source.id)
        if (sourceEntity != null && sourceEntity.type != normalized.source.type.name) {
            throw RemotePayloadNormalizationException(
                "Source type for ${normalized.source.id} is immutable: stored " +
                    "${sourceEntity.type}, incoming ${normalized.source.type.name}"
            )
        }
        val existingOffers = database.offerDao().getBySourceId(normalized.source.id).map { offer ->
            val source = sourceEntity ?: database.sourceInfoDao().getById(offer.sourceId)
            offer.toDomain(requireNotNull(source))
        }
        val subscribedIds = database.userSubscriptionDao().getSubscribedProductIds().toSet()
        val now = clock.instant()
        val changes = SyncChangeDetector.detect(
            existingOffers = existingOffers,
            remoteOffers = normalized.offers,
            baselineEstablished = hadBaseline,
            subscribedProductIds = subscribedIds,
            now = now
        )
        val finishedAt = clock.instant()
        repository.applySync(
            SyncBatch(
                products = normalized.products,
                plans = normalized.plans,
                offers = changes.offersToPersist,
                snapshots = changes.snapshotsToAppend,
                notificationEvents = changes.notificationEvents,
                sources = listOf(normalized.source),
                metadata = SyncMetadata(
                    sourceId = normalized.source.id,
                    status = SyncStatus.SUCCESS,
                    baselineEstablished = true,
                    lastStartedAt = startedAt,
                    lastFinishedAt = finishedAt,
                    lastSuccessfulSyncAt = finishedAt,
                    errorMessage = null
                )
            )
        )
        return SyncResult.Success(
            baselineCreated = !hadBaseline,
            snapshotsAppended = changes.snapshotsToAppend.size,
            notificationEventsCreated = changes.notificationEvents.size
        )
    }

    private suspend fun recordFailure(startedAt: Instant, error: Throwable) {
        val previous = database.syncMetadataDao().getBySourceId(remoteDataSource.sourceId)
        val finishedAt = clock.instant()
        repository.applySync(
            SyncBatch(
                metadata = SyncMetadata(
                    sourceId = remoteDataSource.sourceId,
                    status = SyncStatus.FAILED,
                    baselineEstablished = previous?.baselineEstablished ?: false,
                    lastStartedAt = startedAt,
                    lastFinishedAt = finishedAt,
                    lastSuccessfulSyncAt = previous?.lastSuccessfulSyncAtEpochMillis?.let(
                        Instant::ofEpochMilli
                    ),
                    errorMessage = error.message ?: error.javaClass.simpleName
                )
            )
        )
    }
}
