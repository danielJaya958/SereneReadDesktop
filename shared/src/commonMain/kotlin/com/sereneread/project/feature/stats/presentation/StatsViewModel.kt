package com.sereneread.project.feature.stats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.ui.toUserMessage
import com.sereneread.project.feature.stats.domain.BookStat
import com.sereneread.project.feature.stats.domain.DailyStat
import com.sereneread.project.feature.stats.domain.StatsRepository
import com.sereneread.project.feature.stats.domain.StatsSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class StatsUiState(
    val isLoading: Boolean = true,
    val summary: StatsSummary? = null,
    val daily: List<DailyStat> = emptyList(),
    val books: List<BookStat> = emptyList(),
    val error: String? = null,
)

class StatsViewModel(
    private val repository: StatsRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val today = clock.todayIn(timeZone)
            val summary = repository.getSummary(today)
            val daily = repository.getDaily(today.minus(DatePeriod(days = LAST_DAYS - 1)), today)
            val books = repository.getTopBooks()

            _state.update {
                if (summary is AppResult.Success && daily is AppResult.Success && books is AppResult.Success) {
                    StatsUiState(isLoading = false, summary = summary.data, daily = daily.data, books = books.data)
                } else {
                    val failure = listOf(summary, daily, books).filterIsInstance<AppResult.Failure>().first()
                    it.copy(isLoading = false, error = failure.error.toUserMessage())
                }
            }
        }
    }

    private companion object {
        const val LAST_DAYS = 7
    }
}
