package com.aioffer.radar.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioffer.radar.core.browser.OfficialLaunchResult
import com.aioffer.radar.core.browser.OfficialLinkLauncher
import com.aioffer.radar.core.model.BillingPeriod
import com.aioffer.radar.core.model.Offer
import com.aioffer.radar.core.model.Plan
import com.aioffer.radar.core.model.PriceSnapshot
import com.aioffer.radar.core.model.Product
import com.aioffer.radar.core.model.ProductDetail
import com.aioffer.radar.data.RadarRepository
import com.aioffer.radar.domain.OfferValidityEvaluator
import com.aioffer.radar.domain.OfferValidity
import com.aioffer.radar.domain.OfficialUrlValidator
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val product: Product? = null,
    val plans: List<Plan> = emptyList(),
    val selectedPlanId: String? = null,
    val selectedBillingPeriod: BillingPeriod? = null,
    val availableBillingPeriods: List<BillingPeriod> = emptyList(),
    val currentOffers: List<Offer> = emptyList(),
    val upcomingOffers: List<Offer> = emptyList(),
    val expiredOffers: List<Offer> = emptyList(),
    val history: List<PriceSnapshot> = emptyList(),
    val historicalLowestOfferIds: Set<String> = emptySet()
)

enum class OfficialOfferError {
    INVALID_URL,
    NO_BROWSER,
    OPEN_FAILED
}

class DetailViewModel(
    private val productId: String,
    private val repository: RadarRepository,
    private val clock: Clock,
    private val officialLinkLauncher: OfficialLinkLauncher,
    private val allowHttp: Boolean,
    private val nowTicker: kotlinx.coroutines.flow.Flow<Instant> = flowOf(clock.instant())
) : ViewModel() {
    private val _officialOfferErrors = MutableSharedFlow<OfficialOfferError>(extraBufferCapacity = 1)
    val officialOfferErrors: SharedFlow<OfficialOfferError> = _officialOfferErrors.asSharedFlow()
    private val selectedPlanId = MutableStateFlow<String?>(null)
    private val selectedBillingPeriod = MutableStateFlow<BillingPeriod?>(null)
    private val retrySignal = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredDetail = retrySignal.flatMapLatest {
        combine(
            repository.observeProductDetail(productId),
            selectedPlanId,
            selectedBillingPeriod
        ) { detail, planId, billingPeriod ->
            val resolvedPlanId = planId ?: detail?.plans?.firstOrNull()?.id
            val planOffers = detail?.offers.orEmpty().filter { offer ->
                offer.comparisonKey.planId == resolvedPlanId
            }
            val resolvedPeriod = billingPeriod
                ?: planOffers.firstOrNull()?.comparisonKey?.billingPeriod
            FilteredDetail(detail, resolvedPlanId, resolvedPeriod)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DetailUiState> = retrySignal.flatMapLatest {
        filteredDetail.flatMapLatest { filtered ->
            val comparisonKey = filtered.detail?.offers.orEmpty().firstOrNull { offer ->
                offer.comparisonKey.planId == filtered.selectedPlanId &&
                    offer.comparisonKey.billingPeriod == filtered.selectedBillingPeriod
            }?.comparisonKey
            val historyFlow = if (comparisonKey == null) {
                flowOf(emptyList())
            } else {
                repository.observePriceHistory(comparisonKey)
            }
            combine(historyFlow, nowTicker) { history, now ->
                Triple(filtered, history, now)
            }
        }.map { (filtered, history, now) ->
            val detail = filtered.detail
            val filteredOffers = detail?.offers.orEmpty().filter { offer ->
                offer.comparisonKey.planId == filtered.selectedPlanId &&
                    offer.comparisonKey.billingPeriod == filtered.selectedBillingPeriod
            }
            DetailUiState(
                isLoading = false,
                hasError = detail == null,
                product = detail?.product,
                plans = detail?.plans.orEmpty(),
                selectedPlanId = filtered.selectedPlanId,
                selectedBillingPeriod = filtered.selectedBillingPeriod,
                availableBillingPeriods = detail?.offers.orEmpty().filter { offer ->
                    offer.comparisonKey.planId == filtered.selectedPlanId
                }.map { it.comparisonKey.billingPeriod }.distinct().sortedBy { it.ordinal },
                currentOffers = filteredOffers.filter {
                    OfferValidityEvaluator.evaluate(it, now) == OfferValidity.ACTIVE
                },
                upcomingOffers = filteredOffers.filter {
                    OfferValidityEvaluator.evaluate(it, now) == OfferValidity.NOT_YET_EFFECTIVE
                },
                expiredOffers = filteredOffers.filter {
                    OfferValidityEvaluator.evaluate(it, now) == OfferValidity.EXPIRED
                },
                history = history,
                historicalLowestOfferIds = detail?.historicalLowestOfferIds.orEmpty()
            )
        }.catch {
            emit(DetailUiState(isLoading = false, hasError = true))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DetailUiState()
    )

    fun selectPlan(planId: String) {
        selectedPlanId.value = planId
        selectedBillingPeriod.value = null
    }

    fun selectBillingPeriod(period: BillingPeriod) {
        selectedBillingPeriod.value = period
    }

    /** Recovery entry after a failed local read; re-subscribes the detail flow. */
    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun openOfficialOffer(offerId: String) {
        viewModelScope.launch {
            try {
                val rawUrl = repository.getOfficialOfferUrl(productId, offerId)
                val url = OfficialUrlValidator.validate(rawUrl, allowHttp)
                if (url == null) {
                    _officialOfferErrors.emit(OfficialOfferError.INVALID_URL)
                    return@launch
                }
                when (officialLinkLauncher.open(url)) {
                    OfficialLaunchResult.SUCCESS -> {
                        runCatching {
                            repository.recordOfficialClick(productId, offerId, clock.instant())
                        }
                    }
                    OfficialLaunchResult.NO_BROWSER ->
                        _officialOfferErrors.emit(OfficialOfferError.NO_BROWSER)
                    OfficialLaunchResult.OPEN_FAILED ->
                        _officialOfferErrors.emit(OfficialOfferError.OPEN_FAILED)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _officialOfferErrors.emit(OfficialOfferError.OPEN_FAILED)
            }
        }
    }

    private data class FilteredDetail(
        val detail: ProductDetail?,
        val selectedPlanId: String?,
        val selectedBillingPeriod: BillingPeriod?
    )

    companion object {
        fun factory(
            productId: String,
            repository: RadarRepository,
            clock: Clock,
            officialLinkLauncher: OfficialLinkLauncher,
            allowHttp: Boolean,
            nowTicker: kotlinx.coroutines.flow.Flow<Instant> = flowOf(clock.instant())
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DetailViewModel::class.java)) {
                    "Unknown ViewModel class: ${modelClass.name}"
                }
                @Suppress("UNCHECKED_CAST")
                return DetailViewModel(
                    productId = productId,
                    repository = repository,
                    clock = clock,
                    officialLinkLauncher = officialLinkLauncher,
                    allowHttp = allowHttp,
                    nowTicker = nowTicker
                ) as T
            }
        }
    }
}
