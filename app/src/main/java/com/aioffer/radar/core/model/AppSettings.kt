package com.aioffer.radar.core.model

/**
 * Which dataset the app is currently presenting.
 *
 * The mode only selects what is shown. It never rewrites the trust label of
 * rows that are already stored: every offer keeps the [SourceInfo.type] of the
 * source that produced it. Switching to [OFFICIAL] therefore cannot turn a
 * demo snapshot into official history.
 */
enum class DataSourceMode {
    DEMO,
    OFFICIAL
}

/**
 * Persisted state of the in-app notification prompt.
 *
 * System permission is always read from the platform; this value only records
 * whether the app has already asked/dismissed the prompt so the same banner is
 * not shown again after a process restart.
 */
enum class NotificationPromptState {
    NOT_ASKED,
    PROMPTED
}

data class AppSettings(
    val dataSourceMode: DataSourceMode = DataSourceMode.DEMO,
    val autoSyncEnabled: Boolean = true,
    val notificationPromptState: NotificationPromptState = NotificationPromptState.NOT_ASKED
) {
    val isDemoMode: Boolean
        get() = dataSourceMode == DataSourceMode.DEMO
}
