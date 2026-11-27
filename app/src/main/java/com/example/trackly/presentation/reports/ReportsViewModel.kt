package com.example.trackly.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.locale.weekLocale
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.MonthlyReport
import com.example.trackly.domain.logic.Reports
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class ReportsContent(
    val report: MonthlyReport,
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val pixels: Map<LocalDate, Float>,
    val canGoForward: Boolean,
    val canGoBack: Boolean,
)

class ReportsViewModel(
    habits: HabitRepository,
    private val todayProvider: TodayProvider,
    settings: SettingsRepository,
) : ViewModel() {

    private val month = MutableStateFlow<YearMonth?>(null)

    private val source = RetryableState(viewModelScope) {
        combine(habits.observeHabits(), habits.observeEntries(), todayProvider.today, settings.settings, month) {
                list, entries, today, appSettings, selected ->
            val current = YearMonth.from(today)
            val shown = selected ?: current
            val earliest = list.minOfOrNull { it.startDate }?.let(YearMonth::from) ?: current
            ReportsContent(
                report = Reports.month(list, entries, shown, today),
                today = today,
                firstDay = appSettings.firstDayOfWeek.resolve(weekLocale()),
                pixels = Reports.yearPixels(list, entries, today.year, today),
                canGoForward = shown.isBefore(current),
                canGoBack = shown.isAfter(earliest),
            )
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<UiState<ReportsContent>> = source.state

    fun previousMonth() = shift(-1)

    fun nextMonth() = shift(1)

    private fun shift(by: Long) {
        val current = YearMonth.from(todayProvider.current())
        val next = (month.value ?: current).plusMonths(by)
        month.value = if (next.isAfter(current)) current else next
    }

    fun retry() = source.retry()
}
