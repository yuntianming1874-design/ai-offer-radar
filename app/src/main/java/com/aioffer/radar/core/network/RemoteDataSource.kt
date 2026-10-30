package com.aioffer.radar.core.network

interface RemoteDataSource {
    val sourceId: String

    suspend fun fetch(): RemoteSyncPayload
}

class RemoteDataSourceException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
