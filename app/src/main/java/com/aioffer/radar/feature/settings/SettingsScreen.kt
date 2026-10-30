package com.aioffer.radar.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aioffer.radar.R
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.NotificationPromptState
import com.aioffer.radar.core.model.SyncStatus
import com.aioffer.radar.core.time.DisplayTime

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    notificationsEnabled: Boolean,
    notificationPermissionDenied: Boolean,
    onNotificationPermissionRequest: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsScreen(
        uiState = uiState,
        notificationsEnabled = notificationsEnabled,
        notificationPermissionDenied = notificationPermissionDenied,
        onBack = onBack,
        onDataSourceModeSelected = viewModel::setDataSourceMode,
        onAutoSyncChanged = viewModel::setAutoSyncEnabled,
        onSyncNow = viewModel::syncNow,
        onNotificationAction = {
            viewModel.setNotificationPromptState(NotificationPromptState.PROMPTED)
            onNotificationPermissionRequest()
        },
        onRetryLocalRead = viewModel::retryLocalRead
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    notificationsEnabled: Boolean,
    notificationPermissionDenied: Boolean,
    onBack: () -> Unit,
    onDataSourceModeSelected: (DataSourceMode) -> Unit,
    onAutoSyncChanged: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onNotificationAction: () -> Unit,
    onRetryLocalRead: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.back))
                    }
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "data_source_header") {
                SectionTitle(stringResource(R.string.settings_data_source))
            }
            item(key = "data_source_demo") {
                DataSourceOption(
                    selected = uiState.settings.dataSourceMode == DataSourceMode.DEMO,
                    title = stringResource(R.string.settings_data_source_demo),
                    description = stringResource(R.string.settings_data_source_demo_desc),
                    onClick = { onDataSourceModeSelected(DataSourceMode.DEMO) }
                )
            }
            item(key = "data_source_official") {
                DataSourceOption(
                    selected = uiState.settings.dataSourceMode == DataSourceMode.OFFICIAL,
                    title = stringResource(R.string.settings_data_source_official),
                    description = stringResource(R.string.settings_data_source_official_desc),
                    onClick = { onDataSourceModeSelected(DataSourceMode.OFFICIAL) }
                )
            }
            item(key = "data_source_note") {
                Text(
                    text = stringResource(R.string.settings_data_source_switch_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.settings.dataSourceMode == DataSourceMode.OFFICIAL &&
                !uiState.officialAdapterConfigured
            ) {
                item(key = "official_warning") {
                    WarningSurface(stringResource(R.string.settings_official_not_configured))
                }
            }
            item(key = "sync_header") {
                SectionTitle(stringResource(R.string.settings_last_sync))
            }
            item(key = "sync_info") {
                SyncInfo(uiState, onRetryLocalRead)
            }
            item(key = "sync_button") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onSyncNow,
                        enabled = uiState.isOnline && !uiState.isSyncing
                    ) {
                        Text(
                            stringResource(
                                if (uiState.isSyncing) R.string.syncing else R.string.settings_sync_now
                            )
                        )
                    }
                    if (!uiState.isOnline) {
                        Text(
                            text = stringResource(R.string.settings_sync_offline),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            uiState.operationError?.let { error ->
                item(key = "operation_error") {
                    Text(
                        text = if (error == "offline") {
                            stringResource(R.string.settings_sync_offline)
                        } else {
                            stringResource(R.string.settings_operation_error, error)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            item(key = "divider_1") { HorizontalDivider() }
            item(key = "sync_pref_header") {
                SectionTitle(stringResource(R.string.settings_sync_preference))
            }
            item(key = "auto_sync") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_auto_sync),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_auto_sync_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.settings.autoSyncEnabled,
                        onCheckedChange = onAutoSyncChanged
                    )
                }
            }
            item(key = "divider_2") { HorizontalDivider() }
            item(key = "notifications_header") {
                SectionTitle(stringResource(R.string.settings_notifications))
            }
            item(key = "notifications_status") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (notificationsEnabled) {
                            stringResource(R.string.settings_notifications_enabled)
                        } else {
                            stringResource(
                                if (notificationPermissionDenied) {
                                    R.string.notification_permission_denied
                                } else {
                                    R.string.notification_permission_rationale
                                }
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_notifications_prompt_state,
                            stringResource(
                                if (uiState.settings.notificationPromptState ==
                                    NotificationPromptState.NOT_ASKED
                                ) {
                                    R.string.settings_prompt_not_asked
                                } else {
                                    R.string.settings_prompt_done
                                }
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!notificationsEnabled) {
                        TextButton(onClick = onNotificationAction) {
                            Text(
                                stringResource(
                                    if (notificationPermissionDenied) {
                                        R.string.notification_open_settings
                                    } else {
                                        R.string.notification_enable
                                    }
                                )
                            )
                        }
                    }
                }
            }
            item(key = "divider_3") { HorizontalDivider() }
            item(key = "trust_header") {
                SectionTitle(stringResource(R.string.settings_trust))
            }
            item(key = "trust_text") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.settings_trust_demo),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.settings_trust_official),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DataSourceOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyncInfo(uiState: SettingsUiState, onRetryLocalRead: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = stringResource(
                R.string.settings_sync_status,
                stringResource(uiState.lastSyncStatus.statusLabel())
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = uiState.lastSuccessfulSyncAt?.let {
                stringResource(R.string.settings_last_successful_sync, DisplayTime.format(it))
            } ?: stringResource(R.string.settings_no_successful_sync),
            style = MaterialTheme.typography.bodySmall
        )
        uiState.lastSyncFailureReason?.let { reason ->
            Text(
                text = stringResource(R.string.settings_last_sync_failed, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        uiState.localReadError?.let { error ->
            Text(
                text = stringResource(R.string.settings_local_read_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetryLocalRead) {
                Text(stringResource(R.string.settings_retry_local))
            }
        }
    }
}

@Composable
private fun WarningSurface(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun SyncStatus?.statusLabel(): Int = when (this) {
    SyncStatus.IDLE -> R.string.sync_status_idle
    SyncStatus.RUNNING -> R.string.sync_status_running
    SyncStatus.SUCCESS -> R.string.sync_status_success
    SyncStatus.FAILED -> R.string.sync_status_failed
    null -> R.string.sync_status_idle
}
