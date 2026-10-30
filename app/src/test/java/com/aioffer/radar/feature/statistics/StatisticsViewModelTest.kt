package com.aioffer.radar.feature.statistics

import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.core.model.ProductDetail
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.data.RadarRepository
import com.aioffer.radar.data.SyncBatch
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun statistics_areExposedByProduct() = runTest {
        val initialStatistics = listOf(
            ProductClickCount("kimi", "KIMI", 3),
            ProductClickCount("deepseek", "DeepSeek", 1)
        )
        val repository = FakeStatisticsRepository(initialStatistics)
        val viewModel = StatisticsViewModel(repository)
        advanceUntilIdle()

        assertEquals(initialStatistics, viewModel.uiState.value.statistics)

        val updatedStatistics = listOf(ProductClickCount("kimi", "KIMI", 4))
        repository.statistics.value = updatedStatistics
        advanceUntilIdle()

        assertEquals(updatedStatistics, viewModel.uiState.value.statistics)
    }

    @Test
    fun localReadFailure_canBeRetried() = runTest {
        val initialStatistics = listOf(ProductClickCount("kimi", "KIMI", 3))
        val repository = FakeStatisticsRepository(initialStatistics).apply {
            failRead = true
        }
        val viewModel = StatisticsViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasError)

        repository.failRead = false
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasError)
        assertEquals(initialStatistics, viewModel.uiState.value.statistics)
    }

    @Test
    fun clearClickHistory_clearsPersistedEvents() = runTest {
        val repository = FakeStatisticsRepository(
            listOf(ProductClickCount("kimi", "KIMI", 3))
        )
        val viewModel = StatisticsViewModel(repository)
        advanceUntilIdle()

        viewModel.clearClickHistory()
        advanceUntilIdle()

        assertEquals(1, repository.clearCalls)
        assertEquals(emptyList<ProductClickCount>(), viewModel.uiState.value.statistics)
    }
}

private class FakeStatisticsRepository(
    initialStatistics: List<ProductClickCount>
) : RadarRepository {
    val statistics = MutableStateFlow(initialStatistics)
    var clearCalls = 0
    var failRead = false

    override fun observeClickStatistics(): Flow<List<ProductClickCount>> =
        if (failRead) {
            flow { throw IllegalStateException("disk I/O error") }
        } else {
            statistics
        }

    override suspend fun clearClickHistory() {
        clearCalls++
        statistics.value = emptyList()
    }

    override fun observeHomeProducts(now: Instant): Flow<List<HomeProduct>> =
        MutableStateFlow(emptyList())

    override fun observeProductDetail(productId: String): Flow<ProductDetail?> =
        MutableStateFlow(null)

    override fun observePriceHistory(key: ComparisonKey): Flow<List<PriceSnapshot>> =
        MutableStateFlow(emptyList())

    override fun observeSubscribedProductIds(): Flow<List<String>> = MutableStateFlow(emptyList())

    override fun observeSyncMetadata(sourceId: String): Flow<SyncMetadata?> = MutableStateFlow(null)

    override suspend fun getActiveOffers(productId: String, now: Instant): List<Offer> = emptyList()

    override suspend fun getOfficialOfferUrl(productId: String, offerId: String): String? = null

    override suspend fun applySync(batch: SyncBatch) = Unit

    override suspend fun setSubscribed(productId: String, subscribed: Boolean, at: Instant) = Unit

    override suspend fun subscribeAll(at: Instant) = Unit

    override suspend fun isSubscribed(productId: String): Boolean = false

    override suspend fun recordOfficialClick(productId: String, offerId: String, clickedAt: Instant) = Unit

    override suspend fun hasNotificationEvent(eventId: String): Boolean = false
}
