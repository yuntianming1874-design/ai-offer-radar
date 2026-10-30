package com.aioffer.radar.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aioffer.radar.AiOfferRadarApplication
import com.aioffer.radar.MainActivity
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.NotificationPromptState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val application: AiOfferRadarApplication
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        resetSettings()
    }

    @After
    fun tearDown() = runBlocking {
        resetSettings()
    }

    @Test
    fun settingsEntry_backReturnsHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("AI 优惠雷达")
            composeRule.onNodeWithText("设置").performClick()
            waitForText("同步偏好")

            composeRule.onNodeWithText("返回").performClick()
            waitForText("AI 优惠雷达")
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    @Test
    fun systemBackFromSettings_returnsHomeWithoutLeavingTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("AI 优惠雷达")
            composeRule.onNodeWithText("设置").performClick()
            waitForText("同步偏好")

            Espresso.pressBack()
            waitForText("AI 优惠雷达")
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    @Test
    fun selectedOfficialMode_survivesRecreateAndBackReturnsHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("AI 优惠雷达")
            composeRule.onNodeWithText("设置").performClick()
            waitForText("同步偏好")
            composeRule.onNodeWithText("官方数据").performClick()
            waitForText("来自 GLM Coding Plan 官方套餐页，经本机渲染采集器抓取")

            scenario.recreate()
            waitForText("来自 GLM Coding Plan 官方套餐页，经本机渲染采集器抓取")

            Espresso.pressBack()
            waitForText("数据来源：官方数据")
            composeRule.onNodeWithText("数据来源：官方数据").assertIsDisplayed()
        }
    }

    @Test
    fun selectedOfficialMode_survivesANewActivityLaunch(): Unit = runBlocking {
        application.container.appSettingsStore.setDataSourceMode(DataSourceMode.OFFICIAL)

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("数据来源：官方数据")
            composeRule.onNodeWithText("数据来源：官方数据").assertIsDisplayed()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("数据来源：官方数据")
            composeRule.onNodeWithText("数据来源：官方数据").assertIsDisplayed()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
            }.isSuccess
        }
    }

    private suspend fun resetSettings() {
        application.container.appSettingsStore.setDataSourceMode(DataSourceMode.DEMO)
        application.container.appSettingsStore.setNotificationPromptState(
            NotificationPromptState.NOT_ASKED
        )
        application.container.appSettingsStore.setAutoSyncEnabled(true)
    }
}
