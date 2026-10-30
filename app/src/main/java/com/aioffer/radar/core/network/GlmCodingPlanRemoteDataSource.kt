package com.aioffer.radar.core.network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

const val GLM_CODING_PLAN_SOURCE_ID = "glm-coding-plan-official"
const val DEFAULT_GLM_COLLECTOR_ENDPOINT =
    "http://127.0.0.1:8787/v1/glm-coding-plan"

/**
 * Reads the GLM Coding Plan official-page snapshot produced by
 * tools/glm-collector. The endpoint is a local-only helper because the official
 * page renders its prices with JavaScript; the Android app must never invent
 * prices or scrape the logged-in page itself.
 */
class GlmCodingPlanRemoteDataSource(
    private val endpoint: String = DEFAULT_GLM_COLLECTOR_ENDPOINT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val httpGet: suspend (String) -> String = { url -> httpGetDefault(url) }
) : RemoteDataSource {
    override val sourceId: String = GLM_CODING_PLAN_SOURCE_ID

    override suspend fun fetch(): RemoteSyncPayload = withContext(ioDispatcher) {
        val body = try {
            httpGet(endpoint)
        } catch (error: RemoteDataSourceException) {
            throw error
        } catch (error: Exception) {
            throw RemoteDataSourceException(
                "无法连接 GLM 官方采集器（$endpoint）：" +
                    (error.message ?: error.javaClass.simpleName),
                error
            )
        }
        try {
            parsePayload(body)
        } catch (error: RemoteDataSourceException) {
            throw error
        } catch (error: Exception) {
            throw RemoteDataSourceException(
                "GLM 官方采集器返回的数据无法解析：" +
                    (error.message ?: error.javaClass.simpleName),
                error
            )
        }
    }

    private fun parsePayload(body: String): RemoteSyncPayload {
        val root = Json.parseToJsonElement(body).jsonObject
        val schemaVersion = root.requiredInt("schemaVersion")
        if (schemaVersion != 1) {
            invalid("不支持的采集数据版本：$schemaVersion")
        }
        val source = parseSource(root.requiredObject("source"))
        if (source.id != sourceId) {
            invalid("采集数据来源 ID 不匹配：期望 $sourceId，实际 ${source.id}")
        }
        if (source.sourceUrl.isNullOrBlank()) {
            invalid("官方来源必须提供 sourceUrl")
        }
        if (source.fetchedAtEpochMillis <= 0L) {
            invalid("官方来源必须提供有效的抓取时间")
        }
        val products = root.requiredArray("products").map { parseProduct(it.jsonObject) }
        val plans = root.requiredArray("plans").map { parsePlan(it.jsonObject) }
        val offers = root.requiredArray("offers").map { parseOffer(it.jsonObject) }
        if (products.isEmpty() || plans.isEmpty() || offers.isEmpty()) {
            invalid("官方采集结果不能为空")
        }
        return RemoteSyncPayload(
            source = source,
            products = products,
            plans = plans,
            offers = offers
        )
    }

    private fun parseSource(source: JsonObject) = RemoteSourceDto(
        id = source.requiredString("id"),
        sourceType = source.requiredString("sourceType"),
        sourceUrl = source.optionalString("sourceUrl"),
        titleOrSummary = source.requiredString("titleOrSummary"),
        fetchedAtEpochMillis = source.requiredLong("fetchedAtEpochMillis")
    )

    private fun parseProduct(product: JsonObject) = RemoteProductDto(
        id = product.requiredString("id"),
        name = product.requiredString("name"),
        logoUrl = product.optionalString("logoUrl"),
        isDemo = product.requiredBoolean("isDemo")
    )

    private fun parsePlan(plan: JsonObject) = RemotePlanDto(
        id = plan.requiredString("id"),
        productId = plan.requiredString("productId"),
        name = plan.requiredString("name"),
        planType = plan.requiredString("planType")
    )

    private fun parseOffer(offer: JsonObject) = RemoteOfferDto(
        id = offer.requiredString("id"),
        productId = offer.requiredString("productId"),
        planId = offer.requiredString("planId"),
        planType = offer.requiredString("planType"),
        offerType = offer.requiredString("offerType"),
        title = offer.requiredString("title"),
        discountDescription = offer.requiredString("discountDescription"),
        regionCode = offer.requiredString("regionCode"),
        currencyCode = offer.requiredString("currencyCode"),
        billingPeriod = offer.requiredString("billingPeriod"),
        taxIncluded = offer.requiredBoolean("taxIncluded"),
        originalAmountMinor = offer.requiredLong("originalAmountMinor"),
        actualAmountMinor = offer.requiredLong("actualAmountMinor"),
        monthlyEquivalentMinor = offer.optionalLong("monthlyEquivalentMinor"),
        startsAtEpochMillis = offer.requiredLong("startsAtEpochMillis"),
        endsAtEpochMillis = offer.optionalLong("endsAtEpochMillis"),
        updatedAtEpochMillis = offer.requiredLong("updatedAtEpochMillis"),
        source = parseSource(offer.requiredObject("source")),
        officialUrl = offer.optionalString("officialUrl")
    )

    private fun JsonObject.requiredObject(key: String): JsonObject =
        this[key]?.jsonObject ?: invalid("缺少对象字段 $key")

    private fun JsonObject.requiredArray(key: String): JsonArray =
        this[key]?.jsonArray ?: invalid("缺少数组字段 $key")

    private fun JsonObject.requiredString(key: String): String {
        val value = optionalString(key)
        if (value.isNullOrBlank()) invalid("缺少字符串字段 $key")
        return value
    }

    private fun JsonObject.optionalString(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return element.jsonPrimitive.contentOrNull
    }

    private fun JsonObject.requiredLong(key: String): Long =
        optionalLong(key) ?: invalid("缺少整数字段 $key")

    private fun JsonObject.optionalLong(key: String): Long? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return (element as? JsonPrimitive)?.longOrNull
    }

    private fun JsonObject.requiredInt(key: String): Int {
        val element = this[key] ?: invalid("缺少整数字段 $key")
        return (element as? JsonPrimitive)?.intOrNull ?: invalid("字段 $key 不是整数")
    }

    private fun JsonObject.requiredBoolean(key: String): Boolean {
        val element = this[key] ?: invalid("缺少布尔字段 $key")
        return (element as? JsonPrimitive)?.booleanOrNull
            ?: invalid("字段 $key 不是布尔值")
    }

    private fun invalid(message: String): Nothing =
        throw RemoteDataSourceException(message)
}

private suspend fun httpGetDefault(endpoint: String): String {
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            throw RemoteDataSourceException(
                "GLM 采集器返回 HTTP $responseCode：${body.take(300)}"
            )
        }
        body
    } finally {
        connection.disconnect()
    }
}
