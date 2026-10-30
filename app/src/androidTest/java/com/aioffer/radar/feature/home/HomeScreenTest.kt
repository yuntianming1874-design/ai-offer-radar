package com.aioffer.radar.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.NotificationPromptState
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.testing.databaseOffer
import com.aioffer.radar.ui.theme.AiOfferRadarTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_displaysAppTitleAndDemoWarning() {
        setHomeContent(HomeUiState(contentState = HomeContentState.EMPTY))

        composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        composeRule.onNodeWithText("演示数据，非实时优惠").assertIsDisplayed()
        composeRule.onNodeWithText("暂无可用优惠").assertIsDisplayed()
    }

    @Test
    fun productCard_displaysDetailsAndHandlesSubscription() {
        var selectedProductId: String? = null
        var selectedSubscription: Boolean? = null
        setHomeContent(
            uiState = HomeUiState(
                contentState = HomeContentState.CONTENT,
                products = listOf(testHomeProduct())
            ),
            onSubscriptionChange = { productId, subscribed ->
                selectedProductId = productId
                selectedSubscription = subscribed
            }
        )

        composeRule.onNodeWithText("KIMI").assertIsDisplayed()
        composeRule.onNodeWithText("历史最低").assertIsDisplayed()
        composeRule.onNodeWithText("来源：演示数据").assertIsDisplayed()
        composeRule.onNodeWithText("更新时间：2026-06-01 08:00 北京时间").assertIsDisplayed()
        composeRule.onNodeWithText("¥100.00/月").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("KIMI Logo").assertIsDisplayed()
        composeRule.onNodeWithText("订阅").performClick()

        assertEquals("kimi", selectedProductId)
        assertEquals(true, selectedSubscription)
    }

    @Test
    fun subscribeAllAction_reportsClick() {
        var subscribeAllClicked = false
        setHomeContent(
            uiState = HomeUiState(
                contentState = HomeContentState.CONTENT,
                products = listOf(testHomeProduct())
            ),
            onSubscribeAll = { subscribeAllClicked = true }
        )

        composeRule.onNodeWithText("全部订阅").performClick()

        assertEquals(true, subscribeAllClicked)
    }

    @Test
    fun subscribedProduct_showsUnsubscribeAndReportsChange() {
        var selectedProductId: String? = null
        var selectedSubscription: Boolean? = null
        setHomeContent(
            uiState = HomeUiState(
                contentState = HomeContentState.CONTENT,
                products = listOf(testHomeProduct(isSubscribed = true))
            ),
            onSubscriptionChange = { productId, subscribed ->
                selectedProductId = productId
                selectedSubscription = subscribed
            }
        )

        composeRule.onNodeWithText("取消订阅").performClick()

        assertEquals("kimi", selectedProductId)
        assertEquals(false, selectedSubscription)
    }

    @Test
    fun statisticsAction_reportsNavigationClick() {
        var statisticsClicked = false
        setHomeContent(
            uiState = HomeUiState(contentState = HomeContentState.EMPTY),
            onStatisticsClick = { statisticsClicked = true }
        )

        composeRule.onNodeWithText("点击统计").performClick()

        assertEquals(true, statisticsClicked)
    }

    @Test
    fun syncing_displaysProgressState() {
        setHomeContent(
            HomeUiState(
                contentState = HomeContentState.LOADING,
                isSyncing = true
            )
        )

        composeRule.onNodeWithText("同步中").assertIsDisplayed()
        composeRule.onNodeWithText("正在加载优惠…").assertIsDisplayed()
    }

    @Test
    fun initialError_displaysRetryState() {
        setHomeContent(
            HomeUiState(
                contentState = HomeContentState.ERROR,
                error = HomeError.SYNC
            )
        )

        composeRule.onNodeWithText("同步失败，请稍后重试").assertIsDisplayed()
        composeRule.onNodeWithText("重新同步").assertIsDisplayed()
    }

    @Test
    fun offlineWithCache_displaysWarningAndProducts() {
        setHomeContent(
            HomeUiState(
                contentState = HomeContentState.CONTENT,
                products = listOf(testHomeProduct()),
                isOffline = true,
                error = HomeError.OFFLINE
            )
        )

        composeRule.onNodeWithText("当前无网络，正在展示缓存数据，数据可能已过期").assertIsDisplayed()
        composeRule.onNodeWithText("KIMI").assertIsDisplayed()
    }

    @Test
    fun realMode_hidesAllDemoControls() {
        setHomeContent(
            HomeUiState(
                contentState = HomeContentState.EMPTY,
                isDemoMode = false,
                dataSourceMode = DataSourceMode.OFFICIAL
            )
        )

        composeRule.onNodeWithText("演示数据，非实时优惠").assertDoesNotExist()
        composeRule.onNodeWithText("切换演示场景").assertDoesNotExist()
    }

    @Test
    fun settingsAction_reportsNavigationClick() {
        var settingsClicked = false
        setHomeContent(
            uiState = HomeUiState(contentState = HomeContentState.EMPTY),
            onSettingsClick = { settingsClicked = true }
        )

        composeRule.onNodeWithText("设置").performClick()

        assertEquals(true, settingsClicked)
    }

    @Test
    fun dataSourceStatus_showsLastSuccessfulSyncAndFailureReason() {
        val lastSuccessfulSync = Instant.parse("2026-06-01T00:00:00Z")
        setHomeContent(
            HomeUiState(
                contentState = HomeContentState.CONTENT,
                products = listOf(testHomeProduct()),
                error = HomeError.SYNC,
                lastSuccessfulSyncAt = lastSuccessfulSync,
                lastSyncFailureReason = "demo network failure"
            )
        )

        composeRule.onNodeWithText("数据来源：演示数据").assertIsDisplayed()
        composeRule.onNodeWithText("最后成功同步：2026-06-01 08:00 北京时间").assertIsDisplayed()
        composeRule.onNodeWithText("失败原因：demo network failure").assertIsDisplayed()
    }

    @Test
    fun officialModeWithoutAdapter_showsEmptyStateAndSwitchBack() {
        var switchedBack = false
        setHomeContent(
            uiState = HomeUiState(
                contentState = HomeContentState.EMPTY,
                isDemoMode = false,
                dataSourceMode = DataSourceMode.OFFICIAL,
                officialAdapterConfigured = false
            ),
            onSwitchToDemoData = { switchedBack = true }
        )

        composeRule.onNodeWithText("数据来源：官方数据（适配器未配置）").assertIsDisplayed()
        composeRule.onNodeWithText("暂无官方数据").assertIsDisplayed()
        composeRule.onNodeWithText("切回演示数据").performClick()

        assertEquals(true, switchedBack)
    }

    @Test
    fun databaseError_showsRecoverableRetryEntry() {
        var retried = false
        setHomeContent(
            uiState = HomeUiState(
                contentState = HomeContentState.ERROR,
                error = HomeError.DATABASE,
                databaseErrorMessage = "disk I/O error"
            ),
            onDatabaseRetry = { retried = true }
        )

        composeRule.onNodeWithText("本地数据读取失败，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("本地数据库异常：disk I/O error").assertIsDisplayed()
        composeRule.onNodeWithText("重试读取").performClick()

        assertEquals(true, retried)
    }

    @Test
    fun databaseErrorWithCachedContent_keepsRetryEntryVisible() {
        var retried = false
        setHomeContent(
            uiState = HomeUiState(
                contentState = HomeContentState.CONTENT,
                products = listOf(testHomeProduct()),
                error = HomeError.DATABASE,
                databaseErrorMessage = "disk I/O error"
            ),
            onDatabaseRetry = { retried = true }
        )

        composeRule.onNodeWithText("本地数据库异常：disk I/O error").assertIsDisplayed()
        composeRule.onNodeWithText("重试读取").performClick()

        assertEquals(true, retried)
    }

    @Test
    fun notificationPromptAlreadyShown_isNotShownAgain() {
        composeRule.setContent {
            AiOfferRadarTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        contentState = HomeContentState.EMPTY,
                        notificationPromptState = NotificationPromptState.PROMPTED
                    ),
                    notificationsEnabled = false,
                    notificationPermissionDenied = true
                )
            }
        }

        composeRule.onNodeWithText("通知权限未开启，提醒将在开启后发送").assertDoesNotExist()
    }

    @Test
    fun deniedNotificationPermission_displaysSettingsState() {
        composeRule.setContent {
            AiOfferRadarTheme {
                HomeScreen(
                    uiState = HomeUiState(contentState = HomeContentState.EMPTY),
                    notificationsEnabled = false,
                    notificationPermissionDenied = true
                )
            }
        }

        composeRule.onNodeWithText("通知权限未开启，提醒将在开启后发送").assertIsDisplayed()
        composeRule.onNodeWithText("前往设置").assertIsDisplayed()
    }

    private fun setHomeContent(
        uiState: HomeUiState,
        onSubscriptionChange: (String, Boolean) -> Unit = { _, _ -> },
        onSubscribeAll: () -> Unit = {},
        onStatisticsClick: () -> Unit = {},
        onSettingsClick: () -> Unit = {},
        onSwitchToDemoData: () -> Unit = {},
        onDatabaseRetry: () -> Unit = {}
    ) {
        composeRule.setContent {
            AiOfferRadarTheme {
                HomeScreen(
                    uiState = uiState,
                    onSubscriptionChange = onSubscriptionChange,
                    onSubscribeAll = onSubscribeAll,
                    onStatisticsClick = onStatisticsClick,
                    onSettingsClick = onSettingsClick,
                    onSwitchToDemoData = onSwitchToDemoData,
                    onDatabaseRetry = onDatabaseRetry
                )
            }
        }
    }

    private fun testHomeProduct(isSubscribed: Boolean = false): HomeProduct {
        val offer = databaseOffer()
        return HomeProduct(
            product = Product(id = "kimi", name = "KIMI", isDemo = true),
            activeOffers = listOf(offer),
            isSubscribed = isSubscribed,
            historicalLowestOfferIds = setOf(offer.id)
        )
    }
}
