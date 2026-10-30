package com.aioffer.radar.feature.statistics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.ui.theme.AiOfferRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statistics_displayProductNamesAndCounts() {
        setStatisticsContent(
            StatisticsUiState(
                isLoading = false,
                statistics = listOf(
                    ProductClickCount("kimi", "KIMI", 3),
                    ProductClickCount("deepseek", "DeepSeek", 1)
                )
            )
        )

        composeRule.onNodeWithText("KIMI").assertIsDisplayed()
        composeRule.onNodeWithText("跳转次数：3").assertIsDisplayed()
        composeRule.onNodeWithText("DeepSeek").assertIsDisplayed()
        composeRule.onNodeWithText("跳转次数：1").assertIsDisplayed()
    }

    @Test
    fun clear_requiresConfirmation() {
        var clearCalls = 0
        setStatisticsContent(
            uiState = StatisticsUiState(
                isLoading = false,
                statistics = listOf(ProductClickCount("kimi", "KIMI", 3))
            ),
            onClear = { clearCalls++ }
        )

        composeRule.onNodeWithText("清除记录").performClick()
        composeRule.onNodeWithText("确定要清除所有本地点击记录吗？此操作无法撤销。").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        assertEquals(0, clearCalls)

        composeRule.onNodeWithText("清除记录").performClick()
        composeRule.onNodeWithText("确认清除").performClick()
        assertEquals(1, clearCalls)
    }

    @Test
    fun emptyStatistics_disableClearingAndShowEmptyState() {
        var backClicked = false
        setStatisticsContent(
            uiState = StatisticsUiState(isLoading = false),
            onBack = { backClicked = true }
        )

        composeRule.onNodeWithText("暂无官网跳转记录").assertIsDisplayed()
        composeRule.onNodeWithText("清除记录").assertIsNotEnabled()
        composeRule.onNodeWithText("返回").performClick()
        assertTrue(backClicked)
    }

    private fun setStatisticsContent(
        uiState: StatisticsUiState,
        onBack: () -> Unit = {},
        onClear: () -> Unit = {}
    ) {
        composeRule.setContent {
            AiOfferRadarTheme {
                StatisticsScreen(
                    uiState = uiState,
                    onBack = onBack,
                    onClear = onClear
                )
            }
        }
    }
}
