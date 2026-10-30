package com.aioffer.radar.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aioffer.radar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeepLinkTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun coldStart_opensRequestedProductAndBackReturnsHome() {
        ActivityScenario.launch<MainActivity>(deepLinkIntent("demo-kimi")).use {
            waitForText("KIMI")
            composeRule.onNodeWithText("KIMI").assertIsDisplayed()
            Espresso.pressBack()
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    @Test
    fun existingActivity_sameProduct_reopensDetailAndBackReturnsHome() {
        ActivityScenario.launch<MainActivity>(deepLinkIntent("demo-kimi")).use { scenario ->
            waitForText("KIMI")
            scenario.onActivity { it.onNewIntent(deepLinkIntent("demo-kimi")) }
            waitForText("KIMI")
            Espresso.pressBack()
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    @Test
    fun existingActivity_differentProduct_showsNewProductAndBackReturnsHome() {
        ActivityScenario.launch<MainActivity>(deepLinkIntent("demo-kimi")).use { scenario ->
            waitForText("KIMI")
            scenario.onActivity { it.onNewIntent(deepLinkIntent("demo-deepseek")) }
            waitForText("DEEPSEEK")
            composeRule.onNodeWithText("DEEPSEEK").assertIsDisplayed()
            Espresso.pressBack()
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    @Test
    fun rotationOnDetail_keepsDetailAndBackReturnsHomeOnce() {
        ActivityScenario.launch<MainActivity>(deepLinkIntent("demo-kimi")).use { scenario ->
            waitForText("KIMI")
            scenario.recreate()
            waitForText("KIMI")
            composeRule.onNodeWithText("KIMI").assertIsDisplayed()
            Espresso.pressBack()
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    @Test
    fun rotationAfterBackToHome_doesNotReopenConsumedDeepLink() {
        ActivityScenario.launch<MainActivity>(deepLinkIntent("demo-kimi")).use { scenario ->
            waitForText("KIMI")
            Espresso.pressBack()
            waitForText("AI 优惠雷达")

            scenario.recreate()
            waitForText("AI 优惠雷达")
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
            composeRule.onNodeWithText("当前有效优惠").assertDoesNotExist()
        }
    }

    @Test
    fun rotationAfterHotDeepLink_keepsLatestProductAndBackReturnsHome() {
        ActivityScenario.launch<MainActivity>(deepLinkIntent("demo-kimi")).use { scenario ->
            waitForText("KIMI")
            scenario.onActivity { it.onNewIntent(deepLinkIntent("demo-deepseek")) }
            waitForText("DEEPSEEK")

            scenario.recreate()
            waitForText("DEEPSEEK")
            composeRule.onNodeWithText("DEEPSEEK").assertIsDisplayed()
            Espresso.pressBack()
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    @Test
    fun multipleHotDeepLinks_backReturnsHomeOnce() {
        ActivityScenario.launch<MainActivity>(deepLinkIntent("demo-kimi")).use { scenario ->
            waitForText("KIMI")
            scenario.onActivity { it.onNewIntent(deepLinkIntent("demo-deepseek")) }
            waitForText("DEEPSEEK")
            scenario.onActivity { it.onNewIntent(deepLinkIntent("demo-glm")) }
            waitForText("GLM")

            Espresso.pressBack()
            composeRule.onNodeWithText("AI 优惠雷达").assertIsDisplayed()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
            }.isSuccess
        }
    }

    private fun deepLinkIntent(productId: String): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("aioffer://product/${Uri.encode(productId)}"),
            context,
            MainActivity::class.java
        ).putExtra(AppRoutes.PRODUCT_ID, productId)
    }
}
