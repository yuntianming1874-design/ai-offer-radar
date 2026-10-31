package com.aioffer.radar.core.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GlmCodingPlanRemoteDataSourceTest {

    @Test
    fun fetch_mapsOfficialQuarterlyOfferAndSourceMetadata() = runBlocking {
        var requestedEndpoint: String? = null
        val dataSource = GlmCodingPlanRemoteDataSource(
            endpoint = "http://collector.test/v1/glm-coding-plan",
            nowEpochMillis = { FIXED_NOW },
            httpGet = { endpoint ->
                requestedEndpoint = endpoint
                validPayload
            }
        )

        val payload = dataSource.fetch()

        assertEquals("http://collector.test/v1/glm-coding-plan", requestedEndpoint)
        assertEquals(GLM_CODING_PLAN_SOURCE_ID, payload.source.id)
        assertEquals("OFFICIAL_WEB", payload.source.sourceType)
        assertEquals("https://www.bigmodel.cn/glm-coding", payload.source.sourceUrl)
        assertEquals(1_790_000_000_000L, payload.source.fetchedAtEpochMillis)
        assertEquals(1, payload.products.size)
        assertEquals(false, payload.products.single().isDemo)
        assertEquals(1, payload.plans.size)
        assertEquals(1, payload.offers.size)

        val offer = payload.offers.single()
        assertEquals("QUARTERLY", offer.billingPeriod)
        assertEquals(35_400L, offer.originalAmountMinor)
        assertEquals(28_320L, offer.actualAmountMinor)
        assertEquals(9_440L, offer.monthlyEquivalentMinor)
        assertEquals(true, offer.taxIncluded)
        assertEquals("https://www.bigmodel.cn/glm-coding", offer.officialUrl)
        assertNull(offer.endsAtEpochMillis)
        assertEquals(GLM_CODING_PLAN_SOURCE_ID, offer.source.id)
    }

    @Test
    fun blankEndpoint_failsBeforeRequestingTheNetwork() = runBlocking {
        var requestedEndpoint: String? = null
        val dataSource = GlmCodingPlanRemoteDataSource(
            endpoint = "   ",
            httpGet = { endpoint ->
                requestedEndpoint = endpoint
                validPayload
            }
        )

        val error = assertThrows(RemoteDataSourceException::class.java) {
            runBlocking { dataSource.fetch() }
        }

        assertTrue(error.message.orEmpty().contains("未配置 GLM 官方采集地址"))
        assertNull(requestedEndpoint)
    }

    @Test
    fun stalePayload_isRejected() {
        val dataSource = GlmCodingPlanRemoteDataSource(
            endpoint = "http://collector.test/v1/glm-coding-plan",
            nowEpochMillis = { FIXED_NOW + MAX_SOURCE_AGE_MILLIS + 1L },
            httpGet = { validPayload }
        )

        val error = assertThrows(RemoteDataSourceException::class.java) {
            runBlocking { dataSource.fetch() }
        }

        assertTrue(error.message.orEmpty().contains("采集数据已过期"))
    }

    @Test
    fun unreachableCollector_reportsActionableError() {
        val dataSource = GlmCodingPlanRemoteDataSource(
            nowEpochMillis = { FIXED_NOW },
            httpGet = { throw IOException("Connection refused") }
        )

        val error = assertThrows(RemoteDataSourceException::class.java) {
            runBlocking { dataSource.fetch() }
        }

        assertTrue(error.message.orEmpty().contains("无法连接 GLM 官方采集器"))
        assertTrue(error.message.orEmpty().contains("Connection refused"))
    }

    @Test
    fun mismatchedSourceId_isRejected() {
        val dataSource = GlmCodingPlanRemoteDataSource(
            nowEpochMillis = { FIXED_NOW },
            httpGet = {
                validPayload.replace(
                    "\"id\": \"$GLM_CODING_PLAN_SOURCE_ID\"",
                    "\"id\": \"other-source\""
                )
            }
        )

        val error = assertThrows(RemoteDataSourceException::class.java) {
            runBlocking { dataSource.fetch() }
        }

        assertTrue(error.message.orEmpty().contains("来源 ID 不匹配"))
    }

    @Test
    fun officialSourceWithoutUrl_isRejected() {
        val dataSource = GlmCodingPlanRemoteDataSource(
            nowEpochMillis = { FIXED_NOW },
            httpGet = { validPayload.replace("\"https://www.bigmodel.cn/glm-coding\"", "null") }
        )

        val error = assertThrows(RemoteDataSourceException::class.java) {
            runBlocking { dataSource.fetch() }
        }

        assertTrue(error.message.orEmpty().contains("官方来源必须提供 sourceUrl"))
    }

    @Test
    fun unsupportedSchemaVersion_isRejected() {
        val dataSource = GlmCodingPlanRemoteDataSource(
            nowEpochMillis = { FIXED_NOW },
            httpGet = { validPayload.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2") }
        )

        val error = assertThrows(RemoteDataSourceException::class.java) {
            runBlocking { dataSource.fetch() }
        }

        assertTrue(error.message.orEmpty().contains("不支持的采集数据版本"))
    }

    @Test
    fun malformedJson_reportsParseError() {
        val dataSource = GlmCodingPlanRemoteDataSource(httpGet = { "not-json" })

        val error = assertThrows(RemoteDataSourceException::class.java) {
            runBlocking { dataSource.fetch() }
        }

        assertTrue(error.message.orEmpty().contains("无法解析"))
    }

    private companion object {
        const val FIXED_NOW = 1_790_000_000_000L

        val validPayload = """
            {
              "schemaVersion": 1,
              "source": {
                "id": "glm-coding-plan-official",
                "sourceType": "OFFICIAL_WEB",
                "sourceUrl": "https://www.bigmodel.cn/glm-coding",
                "titleOrSummary": "GLM Coding Plan 官方套餐页",
                "fetchedAtEpochMillis": 1790000000000
              },
              "products": [
                {
                  "id": "glm-coding-plan-official-lite",
                  "name": "GLM Coding Plan Lite",
                  "logoUrl": null,
                  "isDemo": false
                }
              ],
              "plans": [
                {
                  "id": "glm-coding-plan-official-lite",
                  "productId": "glm-coding-plan-official-lite",
                  "name": "Lite",
                  "planType": "STANDARD"
                }
              ],
              "offers": [
                {
                  "id": "glm-coding-plan-official-lite-quarterly",
                  "productId": "glm-coding-plan-official-lite",
                  "planId": "glm-coding-plan-official-lite",
                  "planType": "STANDARD",
                  "offerType": "PROMOTION",
                  "title": "GLM Coding Plan Lite 连续包季 8折",
                  "discountDescription": "连续包季 8折：¥94.4/月（原价 ¥118/月），季付总价 ¥283.20",
                  "regionCode": "CN",
                  "currencyCode": "CNY",
                  "billingPeriod": "QUARTERLY",
                  "taxIncluded": true,
                  "originalAmountMinor": 35400,
                  "actualAmountMinor": 28320,
                  "monthlyEquivalentMinor": 9440,
                  "startsAtEpochMillis": 1790000000000,
                  "endsAtEpochMillis": null,
                  "updatedAtEpochMillis": 1790000000000,
                  "source": {
                    "id": "glm-coding-plan-official",
                    "sourceType": "OFFICIAL_WEB",
                    "sourceUrl": "https://www.bigmodel.cn/glm-coding",
                    "titleOrSummary": "GLM Coding Plan 官方套餐页",
                    "fetchedAtEpochMillis": 1790000000000
                  },
                  "officialUrl": "https://www.bigmodel.cn/glm-coding"
                }
              ]
            }
        """.trimIndent()
    }
}
