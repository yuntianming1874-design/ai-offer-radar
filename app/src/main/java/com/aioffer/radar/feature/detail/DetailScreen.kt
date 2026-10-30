package com.aioffer.radar.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aioffer.radar.R
import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.DataSourceType
import com.aioffer.radar.core.model.Money
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.time.DisplayTime
import com.aioffer.radar.domain.DiscountResult
import com.aioffer.radar.domain.DiscountCalculator
import com.aioffer.radar.domain.PriceDisplayModelFactory
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val invalidUrlMessage = stringResource(R.string.invalid_official_url)
    val noBrowserMessage = stringResource(R.string.no_browser_available)
    val openFailedMessage = stringResource(R.string.official_offer_open_failed)
    LaunchedEffect(viewModel) {
        viewModel.officialOfferErrors.collect { error ->
            snackbarHostState.showSnackbar(
                when (error) {
                    OfficialOfferError.INVALID_URL -> invalidUrlMessage
                    OfficialOfferError.NO_BROWSER -> noBrowserMessage
                    OfficialOfferError.OPEN_FAILED -> openFailedMessage
                }
            )
        }
    }
    DetailScreen(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onPlanSelected = viewModel::selectPlan,
        onBillingPeriodSelected = viewModel::selectBillingPeriod,
        onOpenOffer = { viewModel.openOfficialOffer(it.id) },
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun DetailScreen(
    uiState: DetailUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPlanSelected: (String) -> Unit = {},
    onBillingPeriodSelected: (BillingPeriod) -> Unit = {},
    onOpenOffer: (Offer) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(title = uiState.product?.name.orEmpty(), onBack = onBack)
        }
    ) { contentPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            uiState.hasError || uiState.product == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.detail_not_found))
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry_read))
                }
            }
            else -> DetailContent(
                uiState = uiState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding()
                ),
                onPlanSelected = onPlanSelected,
                onBillingPeriodSelected = onBillingPeriodSelected,
                onOpenOffer = onOpenOffer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
        }
    )
}

@Composable
private fun DetailContent(
    uiState: DetailUiState,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onPlanSelected: (String) -> Unit,
    onBillingPeriodSelected: (BillingPeriod) -> Unit,
    onOpenOffer: (Offer) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterDropdown(
                    label = stringResource(R.string.plan_filter),
                    selectedText = uiState.plans.firstOrNull { it.id == uiState.selectedPlanId }?.name.orEmpty(),
                    options = uiState.plans,
                    optionLabel = { it.name },
                    onSelected = { onPlanSelected(it.id) },
                    modifier = Modifier.weight(1f)
                )
                FilterDropdown(
                    label = stringResource(R.string.billing_period_filter),
                    selectedText = uiState.selectedBillingPeriod?.let { billingPeriodLabel(it) }.orEmpty(),
                    options = uiState.availableBillingPeriods,
                    optionLabel = { billingPeriodLabel(it) },
                    onSelected = onBillingPeriodSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.current_offers),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleLarge
            )
        }
        if (uiState.currentOffers.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_current_offers),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(uiState.currentOffers, key = { "current-${it.id}" }) { offer ->
                OfferCard(
                    offer = offer,
                    isHistoricalLowest = offer.id in uiState.historicalLowestOfferIds,
                    isCurrent = true,
                    onOpenOffer = onOpenOffer
                )
            }
        }
        if (uiState.upcomingOffers.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.upcoming_offers)) }
            items(uiState.upcomingOffers, key = { "upcoming-${it.id}" }) { offer ->
                OfferCard(
                    offer = offer,
                    isHistoricalLowest = offer.id in uiState.historicalLowestOfferIds,
                    isCurrent = false,
                    onOpenOffer = onOpenOffer
                )
            }
        }
        item { PriceHistory(uiState) }
        if (uiState.expiredOffers.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.expired_offers)) }
            items(uiState.expiredOffers, key = { "expired-${it.id}" }) { offer ->
                OfferCard(
                    offer = offer,
                    isHistoricalLowest = offer.id in uiState.historicalLowestOfferIds,
                    isCurrent = false,
                    onOpenOffer = onOpenOffer
                )
            }
        }
    }
}

@Composable
private fun <T> FilterDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }, enabled = options.isNotEmpty()) {
            Text("$label：$selectedText")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun OfferCard(
    offer: Offer,
    isHistoricalLowest: Boolean,
    isCurrent: Boolean,
    onOpenOffer: (Offer) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = offer.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isHistoricalLowest) {
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
            Text(offer.discountDescription)
            Text(stringResource(R.string.original_price, formatMoney(offer.price.originalAmount)))
            Text(
                text = stringResource(R.string.current_price, formatMoney(offer.price.actualAmount)),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (offer.comparisonKey.billingPeriod == BillingPeriod.YEARLY) {
                PriceDisplayModelFactory.create(offer).monthlyEquivalent?.let { monthly ->
                    Text(stringResource(R.string.monthly_equivalent, formatMoney(monthly)))
                }
            }
            Text(discountText(offer))
            Text(stringResource(R.string.offer_starts_at, DisplayTime.format(offer.startsAt)))
            Text(
                offer.endsAt?.let { stringResource(R.string.offer_ends_at, DisplayTime.format(it)) }
                    ?: stringResource(R.string.offer_no_end)
            )
            Text(stringResource(R.string.product_source, sourceName(offer.source.type)))
            Text(stringResource(R.string.product_updated_at, DisplayTime.format(offer.updatedAt)))
            if (isCurrent && offer.officialUrl != null) {
                TextButton(
                    onClick = { onOpenOffer(offer) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.go_subscribe))
                }
            }
        }
    }
}

@Composable
private fun PriceHistory(uiState: DetailUiState) {
    val period = uiState.selectedBillingPeriod
    val chart = remember(uiState.history, period) {
        period?.let { PriceChartModel.create(uiState.history, it) }
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.price_history),
            style = MaterialTheme.typography.titleLarge
        )
        if (chart == null || !chart.hasEnoughData) {
            Text(stringResource(R.string.chart_insufficient))
            return@Column
        }
        val highest = formatMoney(chart.highest.value)
        val lowest = formatMoney(chart.lowest.value)
        val start = DisplayTime.format(chart.startAt)
        val end = DisplayTime.format(chart.endAt)
        val summary = stringResource(
            R.string.chart_summary,
            chart.points.size,
            highest,
            lowest,
            start,
            end
        )
        val description = stringResource(
            R.string.chart_description,
            chart.currency.name,
            chart.points.size,
            highest,
            lowest,
            start,
            end
        )
        Text(
            stringResource(
                if (chart.semantics == ChartValueSemantics.MONTHLY_EQUIVALENT) {
                    R.string.chart_monthly_equivalent
                } else {
                    R.string.chart_actual_price
                },
                chart.currency.name
            )
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.height(180.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(highest, style = MaterialTheme.typography.labelSmall)
                Text(lowest, style = MaterialTheme.typography.labelSmall)
            }
            PriceLineChart(
                chart = chart,
                description = description,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(start, style = MaterialTheme.typography.labelSmall)
            Text(end, style = MaterialTheme.typography.labelSmall)
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PriceLineChart(
    chart: PriceChartModel,
    description: String,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier
            .height(180.dp)
            .semantics { contentDescription = description }
    ) {
        val left = 12.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 12.dp.toPx()
        drawLine(axisColor, Offset(left, top), Offset(left, bottom))
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom))
        val startMillis = chart.startAt.toEpochMilli()
        val timeRange = (chart.endAt.toEpochMilli() - startMillis).coerceAtLeast(1L)
        val minAmount = chart.lowest.value.amountMinor
        val maxAmount = chart.highest.value.amountMinor
        val amountRange = (maxAmount - minAmount).coerceAtLeast(1L)
        val coordinates = chart.points.map { point ->
            val x = left + (right - left) *
                (point.observedAt.toEpochMilli() - startMillis).toFloat() / timeRange
            val y = if (minAmount == maxAmount) {
                (top + bottom) / 2
            } else {
                bottom - (bottom - top) *
                    (point.value.amountMinor - minAmount).toFloat() / amountRange
            }
            Offset(x, y)
        }
        coordinates.zipWithNext().forEach { (start, end) ->
            drawLine(lineColor, start, end, strokeWidth = 3.dp.toPx())
        }
        coordinates.forEach { point ->
            drawCircle(lineColor, radius = 5.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun discountText(offer: Offer): String = when (
    val result = DiscountCalculator.calculate(offer.price.originalAmount, offer.price.actualAmount)
) {
    is DiscountResult.Calculated -> {
        val percentage = result.fraction.abs().multiply(BigDecimal(100))
            .setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        when {
            result.fraction.signum() > 0 -> stringResource(R.string.discount_decrease, percentage)
            result.fraction.signum() < 0 -> stringResource(R.string.discount_increase, percentage)
            else -> stringResource(R.string.discount_unchanged)
        }
    }
    is DiscountResult.Unavailable -> stringResource(R.string.discount_unchanged)
}

@Composable
private fun billingPeriodLabel(period: BillingPeriod): String = stringResource(
    when (period) {
        BillingPeriod.MONTHLY -> R.string.billing_monthly
        BillingPeriod.YEARLY -> R.string.billing_yearly
        BillingPeriod.ONE_TIME -> R.string.billing_one_time
    }
)

@Composable
private fun sourceName(type: DataSourceType): String = stringResource(
    when (type) {
        DataSourceType.DEMO -> R.string.source_demo
        DataSourceType.OFFICIAL_API -> R.string.source_official_api
        DataSourceType.OFFICIAL_RSS -> R.string.source_official_rss
        DataSourceType.OFFICIAL_WEB -> R.string.source_official_web
    }
)

private fun formatMoney(money: Money): String {
    val amount = BigDecimal.valueOf(money.amountMinor, money.currency.minorUnitDigits).toPlainString()
    return "${money.currency.name} $amount"
}

