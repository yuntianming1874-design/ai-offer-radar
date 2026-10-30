package com.aioffer.radar.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aioffer.radar.core.model.ProductClickCount
import com.aioffer.radar.data.RadarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val statistics: List<ProductClickCount> = emptyList(),
    val clearFailed: Boolean = false
)

class StatisticsViewModel(
    private val repository: RadarRepository
) : ViewModel() {
    private val clearFailed = MutableStateFlow(false)
    private val retrySignal = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatisticsUiState> = retrySignal.flatMapLatest {
        combine(
            repository.observeClickStatistics(),
            clearFailed
        ) { statistics, clearFailed ->
            StatisticsUiState(
                isLoading = false,
                statistics = statistics,
                clearFailed = clearFailed
            )
        }.catch {
            emit(StatisticsUiState(isLoading = false, hasError = true))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StatisticsUiState()
    )

    /** Recovery entry after a failed local read; re-subscribes the statistics flow. */
    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun clearClickHistory() {
        viewModelScope.launch {
            try {
                repository.clearClickHistory()
                clearFailed.value = false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                clearFailed.value = true
            }
        }
    }

    companion object {
        fun factory(repository: RadarRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
                        "Unknown ViewModel class: ${modelClass.name}"
                    }
                    @Suppress("UNCHECKED_CAST")
                    return StatisticsViewModel(repository) as T
                }
            }
    }
}
