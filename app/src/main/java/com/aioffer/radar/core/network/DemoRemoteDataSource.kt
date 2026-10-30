package com.aioffer.radar.core.network

import java.time.Instant

enum class DemoScenario {
    INITIAL_SYNC,
    NEW_OFFER,
    PRICE_DECREASE,
    PRICE_INCREASE,
    REPEAT,
    NETWORK_FAILURE
}

class DemoRemoteDataSource(
    // Production injects a DataStore-backed store. The in-memory default keeps unit tests isolated.
    private val stateStore: DemoStateStore = InMemoryDemoStateStore()
) : RemoteDataSource {
    override val sourceId: String = DEMO_SOURCE_ID

    // Official product entry points only. Demo prices remain fabricated and are never
    // presented as prices published on these pages.
    private val officialUrlsByProductId = mapOf(
        "demo-kimi" to "https://kimi.moonshot.cn/",
        "demo-deepseek" to "https://www.deepseek.com/",
        "demo-glm" to "https://chatglm.cn/"
    )

    private var nextScenario = DemoScenario.INITIAL_SYNC
    private var currentOffers = baselineOffers()
    private var restored = false
    private var scenarioOverridden = false

    fun setNextScenario(scenario: DemoScenario) {
        nextScenario = scenario
        scenarioOverridden = true
    }

    override suspend fun fetch(): RemoteSyncPayload {
        restoreStateOnce()

        if (nextScenario == DemoScenario.NETWORK_FAILURE) {
            persistState()
            throw RemoteDataSourceException("Deterministic demo network failure")
        }

        when (nextScenario) {
            DemoScenario.INITIAL_SYNC -> currentOffers = baselineOffers()
            DemoScenario.NEW_OFFER -> addNewOffer()
            DemoScenario.PRICE_DECREASE -> changeKimiPrice(8_000)
            DemoScenario.PRICE_INCREASE -> changeKimiPrice(11_000)
            DemoScenario.REPEAT -> Unit
            DemoScenario.NETWORK_FAILURE -> Unit
        }

        val payload = payload()
        persistState()
        return payload
    }

    private suspend fun restoreStateOnce() {
        if (restored) return

        val state = stateStore.load()
        if (state != null) {
            currentOffers = offersFromState(state)
            if (!scenarioOverridden) {
                nextScenario = state.scenario
            }
        }
        restored = true
    }

    private suspend fun persistState() {
        val kimi = currentOffers.firstOrNull { it.id == KIMI_WELCOME_OFFER_ID } ?: return
        stateStore.save(
            DemoState(
                scenario = nextScenario,
                kimiActualAmountMinor = kimi.actualAmountMinor,
                kimiOriginalAmountMinor = kimi.originalAmountMinor,
                newOfferAdded = currentOffers.any { it.id == NEW_OFFER_ID }
            )
        )
    }

    private fun offersFromState(state: DemoState): List<RemoteOfferDto> {
        val restoredOffers = baselineOffers().map { offer ->
            if (offer.id == KIMI_WELCOME_OFFER_ID) {
                offer.copy(
                    originalAmountMinor = state.kimiOriginalAmountMinor,
                    actualAmountMinor = state.kimiActualAmountMinor,
                    monthlyEquivalentMinor = state.kimiActualAmountMinor
                )
            } else {
                offer
            }
        }

        return if (state.newOfferAdded && restoredOffers.none { it.id == NEW_OFFER_ID }) {
            restoredOffers + offer(NEW_OFFER_ID, "demo-kimi", 7_000)
        } else {
            restoredOffers
        }
    }

    private fun addNewOffer() {
        if (currentOffers.none { it.id == NEW_OFFER_ID }) {
            currentOffers = currentOffers + offer(
                id = NEW_OFFER_ID,
                productId = "demo-kimi",
                actualAmountMinor = 7_000
            )
        }
    }

    private fun changeKimiPrice(amountMinor: Long) {
        currentOffers = currentOffers.map { offer ->
            if (offer.id == KIMI_WELCOME_OFFER_ID) {
                offer.copy(
                    originalAmountMinor = 12_000,
                    actualAmountMinor = amountMinor,
                    monthlyEquivalentMinor = amountMinor
                )
            } else {
                offer
            }
        }
    }

    private fun payload() = RemoteSyncPayload(
        source = source(),
        products = listOf("kimi", "deepseek", "glm").map { name ->
            RemoteProductDto(
                id = "demo-$name",
                name = name.uppercase(),
                logoUrl = null,
                isDemo = true
            )
        },
        plans = listOf("kimi", "deepseek", "glm").map { name ->
            RemotePlanDto(
                id = "demo-$name-standard",
                productId = "demo-$name",
                name = "Demo Standard",
                planType = "STANDARD"
            )
        },
        offers = currentOffers
    )

    private fun baselineOffers() = listOf(
        offer(KIMI_WELCOME_OFFER_ID, "demo-kimi", 10_000),
        offer("demo-deepseek-welcome", "demo-deepseek", 20_000),
        offer("demo-glm-welcome", "demo-glm", 30_000)
    )

    private fun offer(id: String, productId: String, actualAmountMinor: Long) = RemoteOfferDto(
        id = id,
        productId = productId,
        planId = "$productId-standard",
        planType = "STANDARD",
        offerType = "PROMOTION",
        title = "Demo promotion $id",
        discountDescription = "Deterministic demo data, not a real offer",
        regionCode = "CN",
        currencyCode = "CNY",
        billingPeriod = "MONTHLY",
        taxIncluded = true,
        originalAmountMinor = actualAmountMinor + 2_000,
        actualAmountMinor = actualAmountMinor,
        monthlyEquivalentMinor = actualAmountMinor,
        startsAtEpochMillis = DEMO_START.toEpochMilli(),
        endsAtEpochMillis = null,
        updatedAtEpochMillis = DEMO_FETCHED_AT.toEpochMilli(),
        source = source(),
        officialUrl = officialUrlsByProductId.getValue(productId)
    )

    private fun source() = RemoteSourceDto(
        id = DEMO_SOURCE_ID,
        sourceType = "DEMO",
        sourceUrl = null,
        titleOrSummary = "Deterministic demo data; no official or real-time prices",
        fetchedAtEpochMillis = DEMO_FETCHED_AT.toEpochMilli()
    )

    companion object {
        const val DEMO_SOURCE_ID = "demo-local"
        const val KIMI_WELCOME_OFFER_ID = "demo-kimi-welcome"
        const val NEW_OFFER_ID = "demo-kimi-new-promotion"
        val DEMO_START: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val DEMO_FETCHED_AT: Instant = Instant.parse("2026-06-01T00:00:00Z")
    }
}
