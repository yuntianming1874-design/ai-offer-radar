package com.aioffer.radar.feature.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.ComparisonKey
import com.aioffer.radar.core.model.Currency
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.OfferType
import com.aioffer.radar.core.model.Plan
import com.aioffer.radar.core.model.PlanType
import com.aioffer.radar.core.model.Price
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.model.Region
import com.aioffer.radar.core.model.SourceInfo
import com.aioffer.radar.ui.theme.AiOfferRadarTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentOffer_displaysPricesLowestBadgeAndOpensOfficialOffer() {
        val offer = testOffer(officialUrl = "https://example.com/offer")
        var openedOffer: Offer? = null
        var backClicked = false
        setDetailContent(
            uiState = detailState(currentOffers = listOf(offer)),
            onBack = { backClicked = true },
            onOpenOffer = { openedOffer = it }
        )

        composeRule.onNodeWithText("KIMI").assertIsDisplayed()
        composeRule.onNodeWithText("夏季优惠").assertIsDisplayed()
        composeRule.onNodeWithText("历史最低").assertIsDisplayed()
        composeRule.onNodeWithText("原价：CNY 100.00").assertIsDisplayed()
        composeRule.onNodeWithText("现价：CNY 80.00").assertIsDisplayed()
        composeRule.onNodeWithText("优惠 20%").assertIsDisplayed()
        composeRule.onNodeWithText("去订阅").performClick()
        composeRule.onNodeWithText("返回").performClick()

        assertEquals(offer, openedOffer)
        assertTrue(backClicked)
    }

    @Test
    fun filters_reportSelectedPlanAndBillingPeriod() {
        var selectedPlanId: String? = null
        var selectedPeriod: BillingPeriod? = null
        setDetailContent(
            uiState = detailState(
                plans = listOf(
                    Plan("standard", "kimi", "Standard", PlanType.STANDARD),
                    Plan("pro", "kimi", "Pro", PlanType.TEAM)
                ),
                availableBillingPeriods = listOf(BillingPeriod.MONTHLY, BillingPeriod.YEARLY)
            ),
            onPlanSelected = { selectedPlanId = it },
            onBillingPeriodSelected = { selectedPeriod = it }
        )

        composeRule.onNodeWithText("套餐：Standard").performClick()
        composeRule.onNodeWithText("Pro").performClick()
        composeRule.onNodeWithText("计费周期：月付").performClick()
        composeRule.onNodeWithText("年付").performClick()

        assertEquals("pro", selectedPlanId)
        assertEquals(BillingPeriod.YEARLY, selectedPeriod)
    }

    @Test
    fun yearlyHistory_displaysAccessibleMonthlyEquivalentChart() {
        val key = testKey(BillingPeriod.YEARLY)
        val history = listOf(
            testSnapshot(
                id = "first",
                key = key,
                actualAmountMinor = 12_000,
                observedAt = Instant.parse("2026-01-01T00:00:00Z")
            ),
            testSnapshot(
                id = "second",
                key = key,
                actualAmountMinor = 24_000,
                observedAt = Instant.parse("2026-02-01T00:00:00Z")
            )
        )
        setDetailContent(
            uiState = detailState(
                selectedBillingPeriod = BillingPeriod.YEARLY,
                history = history
            )
        )

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("价格历史"))
        composeRule.onNodeWithText("月均折算（CNY）").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("价格折线图", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("共 2 个历史点", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("最高 CNY 20.00", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("最低 CNY 10.00", substring = true).assertIsDisplayed()
    }

    @Test
    fun insufficientHistory_showsEvidenceMessageInsteadOfChart() {
        setDetailContent(uiState = detailState(history = emptyList()))

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("价格历史"))
        composeRule.onNodeWithText("证据不足，将从首次同步开始积累").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("价格折线图", substring = true).assertDoesNotExist()
    }

    private fun setDetailContent(
        uiState: DetailUiState,
        onBack: () -> Unit = {},
        onPlanSelected: (String) -> Unit = {},
        onBillingPeriodSelected: (BillingPeriod) -> Unit = {},
        onOpenOffer: (Offer) -> Unit = {}
    ) {
        composeRule.setContent {
            AiOfferRadarTheme {
                DetailScreen(
                    uiState = uiState,
                    onBack = onBack,
                    onPlanSelected = onPlanSelected,
                    onBillingPeriodSelected = onBillingPeriodSelected,
                    onOpenOffer = onOpenOffer
                )
            }
        }
    }

    private fun detailState(
        plans: List<Plan> = listOf(Plan("standard", "kimi", "Standard", PlanType.STANDARD)),
        selectedBillingPeriod: BillingPeriod = BillingPeriod.MONTHLY,
        availableBillingPeriods: List<BillingPeriod> = listOf(selectedBillingPeriod),
        currentOffers: List<Offer> = emptyList(),
        history: List<PriceSnapshot> = emptyList()
    ) = DetailUiState(
        isLoading = false,
        product = Product(id = "kimi", name = "KIMI", isDemo = true),
        plans = plans,
        selectedPlanId = plans.firstOrNull()?.id,
        selectedBillingPeriod = selectedBillingPeriod,
        availableBillingPeriods = availableBillingPeriods,
        currentOffers = currentOffers,
        history = history,
        historicalLowestOfferIds = currentOffers.mapTo(mutableSetOf()) { it.id }
    )

    private fun testOffer(officialUrl: String? = null): Offer {
        val startsAt = Instant.parse("2026-01-01T00:00:00Z")
        return Offer(
            id = "offer-1",
            comparisonKey = testKey(),
            planType = PlanType.STANDARD,
            offerType = OfferType.PROMOTION,
            title = "夏季优惠",
            discountDescription = "演示优惠",
            price = Price(
                originalAmount = Money(10_000, Currency.CNY),
                actualAmount = Money(8_000, Currency.CNY)
            ),
            startsAt = startsAt,
            endsAt = null,
            source = SourceInfo(
                id = "demo",
                type = DataSourceType.DEMO,
                sourceUrl = null,
                titleOrSummary = "演示数据",
                fetchedAt = startsAt
            ),
            updatedAt = startsAt,
            officialUrl = officialUrl
        )
    }

    private fun testSnapshot(
        id: String,
        key: ComparisonKey,
        actualAmountMinor: Long,
        observedAt: Instant
    ) = PriceSnapshot(
        id = id,
        comparisonKey = key,
        planType = PlanType.STANDARD,
        price = Price(
            originalAmount = Money(actualAmountMinor, Currency.CNY),
            actualAmount = Money(actualAmountMinor, Currency.CNY)
        ),
        observedAt = observedAt
    )

    private fun testKey(period: BillingPeriod = BillingPeriod.MONTHLY) = ComparisonKey(
        productId = "kimi",
        planId = "standard",
        region = Region.MAINLAND_CHINA,
        currency = Currency.CNY,
        billingPeriod = period,
        taxIncluded = true
    )
}
