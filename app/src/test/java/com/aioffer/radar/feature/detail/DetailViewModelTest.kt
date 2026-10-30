package com.aioffer.radar.feature.detail

import com.aioffer.radar.core.browser.OfficialLaunchResult
import com.aioffer.radar.core.browser.OfficialLinkLauncher
import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.Plan
import com.aioffer.radar.core.model.PlanType
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.core.model.ProductDetail
import com.aioffer.radar.core.model.SyncMetadata
import com.aioffer.radar.core.testing.BASE_COMPARISON_KEY
import com.aioffer.radar.core.testing.testOffer
import com.aioffer.radar.core.testing.testSnapshot
import com.aioffer.radar.data.RadarRepository
import com.aioffer.radar.data.SyncBatch
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-06-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val launcher = FakeOfficialLinkLauncher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialDetail_selectsFirstPlanAndPartitionsOffers() = runTest {
        val repository = FakeDetailRepository(testDetail())

        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals("KIMI", state.product?.name)
        assertEquals("standard", state.selectedPlanId)
        assertEquals(BillingPeriod.MONTHLY, state.selectedBillingPeriod)
        assertEquals(
            listOf(BillingPeriod.MONTHLY, BillingPeriod.YEARLY),
            state.availableBillingPeriods
        )
        assertEquals(listOf("current"), state.currentOffers.map { it.id })
        assertEquals(listOf("upcoming"), state.upcomingOffers.map { it.id })
        assertEquals(listOf("expired"), state.expiredOffers.map { it.id })
        assertEquals(setOf("current"), state.historicalLowestOfferIds)
        assertEquals(key(), repository.historyRequests.last())
    }

    @Test
    fun changingPlanAndPeriod_loadsOnlyMatchingOffersAndHistory() = runTest {
        val repository = FakeDetailRepository(testDetail())
        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        viewModel.selectBillingPeriod(BillingPeriod.YEARLY)
        advanceUntilIdle()

        assertEquals(listOf("yearly"), viewModel.uiState.value.currentOffers.map { it.id })
        assertEquals(key(period = BillingPeriod.YEARLY), repository.historyRequests.last())

        viewModel.selectPlan("pro")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("pro", state.selectedPlanId)
        assertEquals(BillingPeriod.YEARLY, state.selectedBillingPeriod)
        assertEquals(listOf(BillingPeriod.YEARLY), state.availableBillingPeriods)
        assertEquals(listOf("pro-yearly"), state.currentOffers.map { it.id })
        assertEquals(key(planId = "pro", period = BillingPeriod.YEARLY), repository.historyRequests.last())
    }

    @Test
    fun historyUpdates_areExposedInUiState() = runTest {
        val repository = FakeDetailRepository(testDetail())
        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        val snapshots = listOf(testSnapshot(), testSnapshot(id = "snapshot-2"))
        repository.setHistory(key(), snapshots)
        advanceUntilIdle()

        assertEquals(snapshots, viewModel.uiState.value.history)
    }

    @Test
    fun missingProduct_showsNotFoundState() = runTest {
        val viewModel = DetailViewModel(
            "missing",
            FakeDetailRepository(null),
            clock,
            launcher,
            allowHttp = false
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasError)
    }

    @Test
    fun localReadFailure_canBeRetried() = runTest {
        val repository = FakeDetailRepository(testDetail()).apply {
            failDetailRead = true
        }
        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasError)

        repository.failDetailRead = false
        viewModel.retry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasError)
        assertEquals("KIMI", viewModel.uiState.value.product?.name)
    }

    @Test
    fun successfulOfficialLaunch_recordsExactlyOneClickWithCurrentTime() = runTest {
        val repository = FakeDetailRepository(testDetail())
        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        viewModel.openOfficialOffer("current")
        advanceUntilIdle()

        assertEquals(listOf("https://example.com/subscribe"), launcher.openedUrls)
        assertEquals(listOf(OfficialClick("kimi", "current", now)), repository.officialClicks)
    }

    @Test
    fun invalidOfficialUrl_doesNotLaunchOrRecordClick() = runTest {
        val repository = FakeDetailRepository(testDetail()).apply {
            officialUrl = "intent://example.com/subscribe"
        }
        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        viewModel.openOfficialOffer("current")
        advanceUntilIdle()

        assertTrue(launcher.openedUrls.isEmpty())
        assertTrue(repository.officialClicks.isEmpty())
    }

    @Test
    fun unavailableBrowser_doesNotRecordClick() = runTest {
        launcher.result = OfficialLaunchResult.NO_BROWSER
        val repository = FakeDetailRepository(testDetail())
        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        viewModel.openOfficialOffer("current")
        advanceUntilIdle()

        assertEquals(listOf("https://example.com/subscribe"), launcher.openedUrls)
        assertTrue(repository.officialClicks.isEmpty())
    }

    @Test
    fun failedLaunch_doesNotRecordClick() = runTest {
        launcher.result = OfficialLaunchResult.OPEN_FAILED
        val repository = FakeDetailRepository(testDetail())
        val viewModel = DetailViewModel("kimi", repository, clock, launcher, allowHttp = false)
        advanceUntilIdle()

        viewModel.openOfficialOffer("current")
        advanceUntilIdle()

        assertTrue(repository.officialClicks.isEmpty())
    }

    @Test
    fun offerExpiresWhileDetailIsOpen_movesFromCurrentToExpired() = runTest {
        val endsAt = now.plusSeconds(60)
        val ticker = MutableStateFlow(now)
        val soonExpiring = testOffer(
            id = "soon-expiring",
            key = key(),
            endsAt = endsAt
        )
        val repository = FakeDetailRepository(testDetail(extraOffers = listOf(soonExpiring)))
        val viewModel = DetailViewModel(
            "kimi",
            repository,
            clock,
            launcher,
            allowHttp = false,
            nowTicker = ticker
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.currentOffers.any { it.id == "soon-expiring" })
        assertFalse(viewModel.uiState.value.expiredOffers.any { it.id == "soon-expiring" })

        ticker.value = endsAt.plusSeconds(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.currentOffers.any { it.id == "soon-expiring" })
        assertTrue(state.expiredOffers.any { it.id == "soon-expiring" })
    }

    private fun testDetail(extraOffers: List<Offer> = emptyList()): ProductDetail {
        val current = testOffer(id = "current", key = key())
        val upcoming = testOffer(
            id = "upcoming",
            key = key(),
            startsAt = Instant.parse("2026-07-01T00:00:00Z")
        )
        val expired = testOffer(
            id = "expired",
            key = key(),
            endsAt = Instant.parse("2026-05-01T00:00:00Z")
        )
        val yearly = testOffer(id = "yearly", key = key(period = BillingPeriod.YEARLY))
        val proYearly = testOffer(
            id = "pro-yearly",
            key = key(planId = "pro", period = BillingPeriod.YEARLY),
            planType = PlanType.TEAM
        )
        return ProductDetail(
            product = Product(id = "kimi", name = "KIMI", isDemo = true),
            plans = listOf(
                Plan("standard", "kimi", "Standard", PlanType.STANDARD),
                Plan("pro", "kimi", "Pro", PlanType.TEAM)
            ),
            offers = listOf(current, upcoming, expired, yearly, proYearly) + extraOffers,
            historicalLowestOfferIds = setOf(current.id)
        )
    }

    private fun key(
        planId: String = "standard",
        period: BillingPeriod = BillingPeriod.MONTHLY
    ) = BASE_COMPARISON_KEY.copy(planId = planId, billingPeriod = period)
}

private data class OfficialClick(
    val productId: String,
    val offerId: String,
    val clickedAt: Instant
)

private class FakeOfficialLinkLauncher : OfficialLinkLauncher {
    var result = OfficialLaunchResult.SUCCESS
    val openedUrls = mutableListOf<String>()

    override fun open(url: String): OfficialLaunchResult {
        openedUrls += url
        return result
    }
}

private class FakeDetailRepository(initialDetail: ProductDetail?) : RadarRepository {
    val detail = MutableStateFlow(initialDetail)
    val historyRequests = mutableListOf<ComparisonKey>()
    val officialClicks = mutableListOf<OfficialClick>()
    var officialUrl: String? = "https://example.com/subscribe"
    var failDetailRead = false
    private val histories = mutableMapOf<ComparisonKey, MutableStateFlow<List<PriceSnapshot>>>()

    override fun observeProductDetail(productId: String): Flow<ProductDetail?> =
        if (failDetailRead) {
            flow { throw IllegalStateException("disk I/O error") }
        } else {
            detail
        }

    override fun observePriceHistory(key: ComparisonKey): Flow<List<PriceSnapshot>> {
        historyRequests += key
        return histories.getOrPut(key) { MutableStateFlow(emptyList()) }
    }

    override suspend fun recordOfficialClick(productId: String, offerId: String, clickedAt: Instant) {
        officialClicks += OfficialClick(productId, offerId, clickedAt)
    }

    fun setHistory(key: ComparisonKey, snapshots: List<PriceSnapshot>) {
        histories.getOrPut(key) { MutableStateFlow(emptyList()) }.value = snapshots
    }

    override fun observeHomeProducts(now: Instant): Flow<List<HomeProduct>> =
        MutableStateFlow(emptyList())

    override fun observeSubscribedProductIds(): Flow<List<String>> = MutableStateFlow(emptyList())

    override fun observeSyncMetadata(sourceId: String): Flow<SyncMetadata?> = MutableStateFlow(null)

    override fun observeClickStatistics(): Flow<List<ProductClickCount>> =
        MutableStateFlow(emptyList())

    override suspend fun getActiveOffers(productId: String, now: Instant): List<Offer> = emptyList()

    override suspend fun getOfficialOfferUrl(productId: String, offerId: String): String? = officialUrl

    override suspend fun applySync(batch: SyncBatch) = Unit

    override suspend fun setSubscribed(productId: String, subscribed: Boolean, at: Instant) = Unit

    override suspend fun subscribeAll(at: Instant) = Unit

    override suspend fun isSubscribed(productId: String): Boolean = false

    override suspend fun clearClickHistory() = Unit

    override suspend fun hasNotificationEvent(eventId: String): Boolean = false
}
