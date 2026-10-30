package com.aioffer.radar.core.network

/**
 * Stable source ID reserved for the official data-source slot. A real adapter
 * must publish its own source ID and source metadata; this placeholder exists
 * only so "official mode" has an isolated, explicit failure path instead of
 * silently reusing the demo source.
 */
const val OFFICIAL_SOURCE_ID = "official-unconfigured"

/**
 * Placeholder official adapter.
 *
 * The project does not ship a real official adapter yet. Returning data here
 * would misrepresent demo fixtures or fabricated content as official prices,
 * so fetch always fails with a user-visible reason. Switch back to the demo
 * source to inspect demo data; demo rows keep their DEMO source type and are
 * never shown as official history.
 */
class UnconfiguredOfficialRemoteDataSource(
    override val sourceId: String = OFFICIAL_SOURCE_ID
) : RemoteDataSource {
    override suspend fun fetch(): RemoteSyncPayload {
        throw RemoteDataSourceException(
            "官方数据源适配器尚未配置：当前不会产生或缓存任何官方实时数据"
        )
    }
}
