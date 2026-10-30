package com.aioffer.radar.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aioffer.radar.core.model.AppSettings
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.ui.theme.AiOfferRadarTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dataSourceSelection_reportsModeChangesAndShowsBoundaryWarning() {
        var selectedMode: DataSourceMode? = null
        setSettingsContent(
            uiState = SettingsUiState(
                settings = AppSettings(dataSourceMode = DataSourceMode.OFFICIAL),
                officialAdapterConfigured = false
            ),
            onDataSourceModeSelected = { selectedMode = it }
        )

        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("官方数据源适配器尚未配置：当前不会产生或缓存官方实时数据。可随时切回演示数据查看演示内容。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("切换只改变展示范围，不会改写已保存记录的可信度标识。演示记录永远不会被标记为官方历史。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("演示数据").performClick()
        assertEquals(DataSourceMode.DEMO, selectedMode)
    }

    @Test
    fun autoSyncToggle_reportsPreferenceChange() {
        var autoSync: Boolean? = null
        setSettingsContent(
            uiState = SettingsUiState(settings = AppSettings(autoSyncEnabled = true)),
            onAutoSyncChanged = { autoSync = it }
        )

        composeRule.onNode(isToggleable()).performClick()

        assertEquals(false, autoSync)
    }

    @Test
    fun syncStatus_showsLastSuccessAndFailureReason() {
        setSettingsContent(
            uiState = SettingsUiState(
                settings = AppSettings(),
                lastSyncStatus = SyncStatus.FAILED,
                lastSuccessfulSyncAt = Instant.parse("2026-06-01T00:00:00Z"),
                lastSyncFailureReason = "demo network failure"
            )
        )

        composeRule.onNodeWithText("同步状态：失败").assertIsDisplayed()
        composeRule.onNodeWithText("最后成功同步：2026-06-01 08:00 北京时间").assertIsDisplayed()
        composeRule.onNodeWithText("上次同步失败：demo network failure").assertIsDisplayed()
    }

    @Test
    fun offlineSync_disablesSyncNowAndExplainsWhy() {
        setSettingsContent(
            uiState = SettingsUiState(settings = AppSettings(), isOnline = false)
        )

        composeRule.onNodeWithText("立即同步").assertIsDisplayed()
        composeRule.onNodeWithText("当前离线，联网后可同步").assertIsDisplayed()
    }

    @Test
    fun localReadFailure_offersRetryEntry() {
        var retried = false
        setSettingsContent(
            uiState = SettingsUiState(
                settings = AppSettings(),
                localReadError = "disk I/O error"
            ),
            onRetryLocalRead = { retried = true }
        )

        composeRule.onNodeWithText("本地同步信息读取失败：disk I/O error").assertIsDisplayed()
        composeRule.onNodeWithText("重新读取").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun notificationDenied_offersPermissionAction() {
        var requested = false
        setSettingsContent(
            uiState = SettingsUiState(settings = AppSettings()),
            notificationsEnabled = false,
            notificationPermissionDenied = true,
            onNotificationAction = { requested = true }
        )

        composeRule.onNodeWithText("通知权限未开启，提醒将在开启后发送").assertIsDisplayed()
        composeRule.onNodeWithText("前往设置").performClick()
        assertEquals(true, requested)
    }

    @Test
    fun back_reportsNavigation() {
        var back = false
        setSettingsContent(
            uiState = SettingsUiState(settings = AppSettings()),
            onBack = { back = true }
        )

        composeRule.onNodeWithText("返回").performClick()
        assertEquals(true, back)
    }

    private fun setSettingsContent(
        uiState: SettingsUiState,
        notificationsEnabled: Boolean = true,
        notificationPermissionDenied: Boolean = false,
        onBack: () -> Unit = {},
        onDataSourceModeSelected: (DataSourceMode) -> Unit = {},
        onAutoSyncChanged: (Boolean) -> Unit = {},
        onSyncNow: () -> Unit = {},
        onNotificationAction: () -> Unit = {},
        onRetryLocalRead: () -> Unit = {}
    ) {
        composeRule.setContent {
            AiOfferRadarTheme {
                SettingsScreen(
                    uiState = uiState,
                    notificationsEnabled = notificationsEnabled,
                    notificationPermissionDenied = notificationPermissionDenied,
                    onBack = onBack,
                    onDataSourceModeSelected = onDataSourceModeSelected,
                    onAutoSyncChanged = onAutoSyncChanged,
                    onSyncNow = onSyncNow,
                    onNotificationAction = onNotificationAction,
                    onRetryLocalRead = onRetryLocalRead
                )
            }
        }
    }
}
