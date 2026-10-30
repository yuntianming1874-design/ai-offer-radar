package com.aioffer.radar.core.network

import com.aioffer.radar.domain.OfficialUrlValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DemoRemoteDataSourceTest {
    @Test
    fun initialPayload_usesExplicitDemoIdentifiers() = runBlocking {
        val payload = DemoRemoteDataSource().fetch()

        assertEquals("DEMO", payload.source.sourceType)
        assertTrue(payload.products.all { it.isDemo && it.id.startsWith("demo-") })
        assertTrue(payload.plans.all { it.id.startsWith("demo-") })
        assertTrue(payload.offers.all { it.id.startsWith("demo-") })
    }

    @Test
    fun newOfferAndRepeat_returnDeterministicPayloads() = runBlocking {
        val source = DemoRemoteDataSource()
        source.fetch()
        source.setNextScenario(DemoScenario.NEW_OFFER)
        val changed = source.fetch()
        source.setNextScenario(DemoScenario.REPEAT)
        val repeated = source.fetch()

        assertTrue(changed.offers.any { it.id == DemoRemoteDataSource.NEW_OFFER_ID })
        assertEquals(changed, repeated)
    }

    @Test
    fun priceScenarios_returnSpecifiedAmounts() = runBlocking {
        val source = DemoRemoteDataSource()
        source.fetch()
        source.setNextScenario(DemoScenario.PRICE_DECREASE)
        val decreased = source.fetch()
        source.setNextScenario(DemoScenario.PRICE_INCREASE)
        val increased = source.fetch()

        assertEquals(8_000L, decreased.offers.first { it.id == "demo-kimi-welcome" }.actualAmountMinor)
        assertEquals(8_000L, decreased.offers.first { it.id == "demo-kimi-welcome" }.monthlyEquivalentMinor)
        assertEquals(11_000L, increased.offers.first { it.id == "demo-kimi-welcome" }.actualAmountMinor)
        assertEquals(11_000L, increased.offers.first { it.id == "demo-kimi-welcome" }.monthlyEquivalentMinor)
    }

    @Test
    fun everyDemoOffer_hasAValidHttpsOfficialUrl() = runBlocking {
        val payload = DemoRemoteDataSource().fetch()

        payload.offers.forEach { offer ->
            assertNotNull("${offer.id} has no official URL", offer.officialUrl)
            assertEquals(
                "Unsafe or invalid official URL for ${offer.id}",
                offer.officialUrl,
                OfficialUrlValidator.validate(offer.officialUrl)
            )
        }
    }

    @Test
    fun networkFailure_doesNotReplaceLastSuccessfulPayload() = runBlocking {
        val source = DemoRemoteDataSource()
        val initial = source.fetch()
        source.setNextScenario(DemoScenario.NETWORK_FAILURE)
        try {
            source.fetch()
            fail("Expected deterministic network failure")
        } catch (_: RemoteDataSourceException) {
        }
        source.setNextScenario(DemoScenario.REPEAT)

        assertEquals(initial, source.fetch())
    }

    @Test
    fun processRestart_restoresPriceDecreaseWithoutCreatingANewChange() = runBlocking {
        val store = InMemoryDemoStateStore()
        val firstProcess = DemoRemoteDataSource(store)
        firstProcess.fetch()
        firstProcess.setNextScenario(DemoScenario.PRICE_DECREASE)
        val decreased = firstProcess.fetch()

        val restartedProcess = DemoRemoteDataSource(store)
        val repeated = restartedProcess.fetch()

        assertEquals(decreased, repeated)
        val kimi = repeated.offers.first { it.id == "demo-kimi-welcome" }
        assertEquals(8_000L, kimi.actualAmountMinor)
        assertEquals(8_000L, kimi.monthlyEquivalentMinor)
    }

    @Test
    fun processRestart_restoresNewOfferWithoutDuplicatingIt() = runBlocking {
        val store = InMemoryDemoStateStore()
        val firstProcess = DemoRemoteDataSource(store)
        firstProcess.fetch()
        firstProcess.setNextScenario(DemoScenario.NEW_OFFER)
        val changed = firstProcess.fetch()

        val restartedProcess = DemoRemoteDataSource(store)
        val repeated = restartedProcess.fetch()

        assertEquals(changed, repeated)
        assertEquals(
            1,
            repeated.offers.count { it.id == DemoRemoteDataSource.NEW_OFFER_ID }
        )
    }

    @Test
    fun processRestart_restoresNetworkFailureScenario() = runBlocking {
        val store = InMemoryDemoStateStore()
        val firstProcess = DemoRemoteDataSource(store)
        firstProcess.fetch()
        firstProcess.setNextScenario(DemoScenario.NETWORK_FAILURE)
        try {
            firstProcess.fetch()
            fail("Expected deterministic network failure")
        } catch (_: RemoteDataSourceException) {
        }

        val restartedProcess = DemoRemoteDataSource(store)
        try {
            restartedProcess.fetch()
            fail("Expected deterministic network failure after restart")
        } catch (_: RemoteDataSourceException) {
        }
    }
}
