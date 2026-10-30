package com.aioffer.radar.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aioffer.radar.R
import com.aioffer.radar.core.model.DataSourceMode
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.HomeProduct
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.NotificationPromptState
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.time.DisplayTime
import com.aioffer.radar.core.network.DemoScenario
import java.math.BigDecimal

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    notificationsEnabled: Boolean = true,
    notificationPermissionDenied: Boolean = false,
    onNotificationPermissionRequest: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProductClick: (String) -> Unit = {},
    onStatisticsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreen(
        uiState = uiState,
        notificationsEnabled = notificationsEnabled,
        notificationPermissionDenied = notificationPermissionDenied,
        onNotificationPermissionRequest = {
            viewModel.markNotificationPromptShown()
            onNotificationPermissionRequest()
        },
        onNotificationPromptDismiss = viewModel::dismissNotificationPrompt,
        onRefresh = viewModel::refresh,
        onDatabaseRetry = viewModel::retryAfterDatabaseError,
        onSwitchToDemoData = { viewModel.setDataSourceMode(DataSourceMode.DEMO) },
        onSubscribeAll = viewModel::subscribeAll,
        onSubscriptionChange = viewModel::setSubscribed,
        onDemoScenarioSelected = viewModel::selectDemoScenario,
        onSettingsClick = onSettingsClick,
        onProductClick = onProductClick,
        onStatisticsClick = onStatisticsClick
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    notificationsEnabled: Boolean = true,
    notificationPermissionDenied: Boolean = false,
    onNotificationPermissionRequest: () -> Unit = {},
    onNotificationPromptDismiss: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDatabaseRetry: () -> Unit = {},
    onSwitchToDemoData: () -> Unit = {},
    onSubscribeAll: () -> Unit = {},
    onSubscriptionChange: (String, Boolean) -> Unit = { _, _ -> },
    onDemoScenarioSelected: (DemoScenario) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProductClick: (String) -> Unit = {},
    onStatisticsClick: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HomeTopBar(
                uiState = uiState,
                onRefresh = onRefresh,
                onSubscribeAll = onSubscribeAll,
                onDemoScenarioSelected = onDemoScenarioSelected,
                onSettingsClick = onSettingsClick,
                onStatisticsClick = onStatisticsClick
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            if (!notificationsEnabled &&
                uiState.notificationPromptState == NotificationPromptState.NOT_ASKED
            ) {
                NotificationPermissionBanner(
                    denied = notificationPermissionDenied,
                    onAction = onNotificationPermissionRequest,
                    onDismiss = onNotificationPromptDismiss
                )
            }
            DataSourceStatusCard(uiState, onDatabaseRetry)
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.contentState) {
                    HomeContentState.LOADING -> LoadingContent()
                    HomeContentState.EMPTY -> EmptyContent(
                        uiState = uiState,
                        onRefresh = onRefresh,
                        onSwitchToDemoData = onSwitchToDemoData
                    )
                    HomeContentState.ERROR -> ErrorContent(
                        uiState = uiState,
                        onRefresh = onRefresh,
                        onDatabaseRetry = onDatabaseRetry,
                        onSwitchToDemoData = onSwitchToDemoData
                    )
                    HomeContentState.CONTENT -> ProductList(
                        uiState = uiState,
                        onSubscriptionChange = onSubscriptionChange,
                        onProductClick = onProductClick
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(
    denied: Boolean,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (denied) R.string.notification_permission_denied
                    else R.string.notification_permission_rationale
                ),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.notification_prompt_later))
            }
            TextButton(onClick = onAction) {
                Text(
                    stringResource(
                        if (denied) R.string.notification_open_settings
                        else R.string.notification_enable
                    )
                )
            }
        }
    }
}

/**
 * Always-visible provenance block. It intentionally never says "real-time":
 * it names the selected source and the last successful sync, and repeats the
 * failure reason stored with the source. Cached data is marked as potentially
 * stale when the device is offline.
 */
@Composable
private fun DataSourceStatusCard(
    uiState: HomeUiState,
    onDatabaseRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.data_source_status, uiState.dataSourceLabel()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val lastSuccessfulSync = uiState.lastSuccessfulSyncAt
            Text(
                text = if (lastSuccessfulSync == null) {
                    stringResource(R.string.no_successful_sync)
                } else {
                    stringResource(
                        R.string.last_successful_sync,
                        DisplayTime.format(lastSuccessfulSync)
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.isOffline) {
                Text(
                    text = stringResource(
                        if (uiState.hasData) {
                            R.string.data_source_offline_cached
                        } else {
                            R.string.offline_no_cache
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.lastSyncFailureReason?.let { reason ->
                Text(
                    text = stringResource(R.string.sync_failure_reason, reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            // A metadata read can fail while cached products are still shown.
            // Keep the recovery entry visible instead of only surfacing it in
            // the full-screen error state.
            if (uiState.error == HomeError.DATABASE &&
                uiState.contentState == HomeContentState.CONTENT
            ) {
                uiState.databaseErrorMessage?.let { reason ->
                    Text(
                        text = stringResource(R.string.database_read_failed_reason, reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDatabaseRetry) {
                    Text(stringResource(R.string.retry_read))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onSubscribeAll: () -> Unit,
    onDemoScenarioSelected: (DemoScenario) -> Unit,
    onSettingsClick: () -> Unit,
    onStatisticsClick: () -> Unit
) {
    Column {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                TextButton(onClick = onStatisticsClick) {
                    Text(stringResource(R.string.click_statistics))
                }
                TextButton(
                    onClick = onSubscribeAll,
                    enabled = uiState.products.any { !it.isSubscribed }
                ) {
                    Text(stringResource(R.string.subscribe_all))
                }
                TextButton(onClick = onRefresh, enabled = !uiState.isSyncing) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.syncing))
                    } else {
                        Text(stringResource(R.string.sync))
                    }
                }
                TextButton(onClick = onSettingsClick) {
                    Text(stringResource(R.string.settings_entry))
                }
            }
        )
        if (uiState.dataSourceMode == DataSourceMode.DEMO) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = stringResource(R.string.demo_data_banner),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            DemoScenarioSelector(
                enabled = !uiState.isSyncing,
                onScenarioSelected = onDemoScenarioSelected
            )
        }
    }
}

@Composable
private fun DemoScenarioSelector(
    enabled: Boolean,
    onScenarioSelected: (DemoScenario) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(R.string.demo_scenario_switch))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DemoScenario.entries.forEach { scenario ->
                DropdownMenuItem(
                    text = { Text(stringResource(scenario.labelResource())) },
                    onClick = {
                        expanded = false
                        onScenarioSelected(scenario)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProductList(
    uiState: HomeUiState,
    onSubscriptionChange: (String, Boolean) -> Unit,
    onProductClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        uiState.error?.let { error ->
            item(key = "status") {
                StatusBanner(error = error, hasCachedData = uiState.isShowingCachedData)
            }
        }
        items(uiState.products, key = { it.product.id }) { homeProduct ->
            ProductCard(
                homeProduct = homeProduct,
                onSubscriptionChange = onSubscriptionChange,
                onProductClick = onProductClick
            )
        }
    }
}

@Composable
private fun ProductCard(
    homeProduct: HomeProduct,
    onSubscriptionChange: (String, Boolean) -> Unit,
    onProductClick: (String) -> Unit
) {
    val product = homeProduct.product
    val offer = homeProduct.activeOffers.minByOrNull { it.price.actualAmount.amountMinor }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = product.logoUrl,
                    contentDescription = stringResource(R.string.product_logo, product.name),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_product_placeholder),
                    error = painterResource(R.drawable.ic_product_placeholder),
                    fallback = painterResource(R.drawable.ic_product_placeholder)
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (offer != null && offer.id in homeProduct.historicalLowestOfferIds) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = stringResource(R.string.historical_lowest),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    offer?.let {
                        Text(
                            text = stringResource(
                                R.string.product_source,
                                it.source.type.displayName()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.product_updated_at,
                                DisplayTime.format(it.updatedAt)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (offer == null) {
                Text(stringResource(R.string.no_active_offer))
            } else {
                Text(text = offer.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatOfferPrice(offer),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { onProductClick(product.id) }) {
                    Text(stringResource(R.string.view_details))
                }
                TextButton(
                    onClick = { onSubscriptionChange(product.id, !homeProduct.isSubscribed) }
                ) {
                    Text(
                        stringResource(
                            if (homeProduct.isSubscribed) R.string.unsubscribe else R.string.subscribe
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(error: HomeError, hasCachedData: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = stringResource(error.messageResource(hasCachedData)),
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.loading_offers),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun EmptyContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onSwitchToDemoData: () -> Unit
) {
    val isOfficial = uiState.dataSourceMode == DataSourceMode.OFFICIAL
    MessageContent(
        title = stringResource(
            if (isOfficial) R.string.official_empty_offers else R.string.empty_offers
        ),
        description = if (isOfficial) stringResource(R.string.official_empty_offers_desc) else null,
        action = stringResource(R.string.retry_sync),
        onAction = onRefresh,
        secondaryAction = if (isOfficial) stringResource(R.string.switch_to_demo_data) else null,
        onSecondaryAction = onSwitchToDemoData
    )
}

@Composable
private fun ErrorContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onDatabaseRetry: () -> Unit,
    onSwitchToDemoData: () -> Unit
) {
    val isDatabaseError = uiState.error == HomeError.DATABASE
    val title = when {
        isDatabaseError -> stringResource(R.string.database_read_failed)
        else -> stringResource(
            (uiState.error ?: HomeError.SYNC).messageResource(hasCachedData = false)
        )
    }
    val description = when {
        isDatabaseError && uiState.databaseErrorMessage != null ->
            stringResource(R.string.database_read_failed_reason, uiState.databaseErrorMessage)
        else -> null
    }
    MessageContent(
        title = title,
        description = description,
        action = stringResource(if (isDatabaseError) R.string.retry_read else R.string.retry_sync),
        onAction = if (isDatabaseError) onDatabaseRetry else onRefresh,
        secondaryAction = if (uiState.dataSourceMode == DataSourceMode.OFFICIAL) {
            stringResource(R.string.switch_to_demo_data)
        } else {
            null
        },
        onSecondaryAction = onSwitchToDemoData
    )
}

@Composable
private fun MessageContent(
    title: String,
    description: String? = null,
    action: String,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondaryAction: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        description?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) {
            Text(action)
        }
        secondaryAction?.let {
            TextButton(onClick = onSecondaryAction) {
                Text(it)
            }
        }
    }
}

private fun HomeError.messageResource(hasCachedData: Boolean): Int = when (this) {
    HomeError.OFFLINE -> if (hasCachedData) R.string.offline_cached else R.string.offline_no_cache
    HomeError.SYNC -> if (hasCachedData) R.string.sync_failed_cached else R.string.sync_failed_no_cache
    HomeError.SUBSCRIPTION -> R.string.subscription_failed
    HomeError.DATABASE -> R.string.database_read_failed
}

private fun DemoScenario.labelResource(): Int = when (this) {
    DemoScenario.INITIAL_SYNC -> R.string.demo_initial_sync
    DemoScenario.NEW_OFFER -> R.string.demo_new_offer
    DemoScenario.PRICE_DECREASE -> R.string.demo_price_decrease
    DemoScenario.PRICE_INCREASE -> R.string.demo_price_increase
    DemoScenario.REPEAT -> R.string.demo_repeat
    DemoScenario.NETWORK_FAILURE -> R.string.demo_network_failure
}

@Composable
private fun HomeUiState.dataSourceLabel(): String = when {
    dataSourceMode == DataSourceMode.DEMO -> stringResource(R.string.source_demo)
    officialAdapterConfigured -> stringResource(R.string.data_source_official)
    else -> stringResource(R.string.data_source_official_unconfigured)
}

@Composable
private fun DataSourceType.displayName(): String = stringResource(
    when (this) {
        DataSourceType.DEMO -> R.string.source_demo
        DataSourceType.OFFICIAL_API -> R.string.source_official_api
        DataSourceType.OFFICIAL_RSS -> R.string.source_official_rss
        DataSourceType.OFFICIAL_WEB -> R.string.source_official_web
    }
)

@Composable
private fun formatOfferPrice(offer: Offer): String {
    val price = formatMoney(offer.price.actualAmount)
    return when (offer.comparisonKey.billingPeriod) {
        com.aioffer.radar.core.model.BillingPeriod.MONTHLY ->
            stringResource(R.string.price_monthly, price)
        com.aioffer.radar.core.model.BillingPeriod.YEARLY ->
            stringResource(R.string.price_yearly, price)
        com.aioffer.radar.core.model.BillingPeriod.ONE_TIME -> price
    }
}

private fun formatMoney(money: Money): String {
    val amount = BigDecimal.valueOf(money.amountMinor, money.currency.minorUnitDigits).toPlainString()
    return when (money.currency) {
        com.aioffer.radar.core.model.Currency.CNY -> "¥$amount"
        com.aioffer.radar.core.model.Currency.USD -> "$$amount"
    }
}
