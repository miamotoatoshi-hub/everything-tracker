package com.example.trackly.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.logic.DayStateResolver
import com.example.trackly.domain.logic.StatsCalculator
import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.logic.WeeklySummary
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

data class WeeklyHabitRow(
    val habit: Habit,
    val states: List<DayState>,
    val completed: Int,
    val streak: Int,
)

data class WeeklyOverview(
    val summary: WeeklySummary,
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val rows: List<WeeklyHabitRow>,
)

class WeeklyOverviewViewModel(
    habits: HabitRepository,
    todayProvider: TodayProvider,
    settings: SettingsRepository,
) : ViewModel() {

    private val source = RetryableState(viewModelScope) {
        combine(
            habits.observeHabits(),
            habits.observeEntries(),
            todayProvider.today,
            settings.settings,
        ) { list, entries, today, appSettings ->
            val active = list.filter { !it.isArchived }
            val firstDay = appSettings.firstDayOfWeek.resolve(Locale.getDefault())
            val summary = StatsCalculator.weeklySummary(active, entries, today, firstDay)
            val byHabit = entries.groupBy { it.habitId }
            val rows = active.map { habit ->
                val own = byHabit[habit.id].orEmpty()
                val byDate = own.associateBy { it.date }
                val states = summary.range.days().map { DayStateResolver.resolve(habit, byDate[it], it, today) }
                WeeklyHabitRow(
                    habit = habit,
                    states = states,
                    completed = states.count { it == DayState.COMPLETED },
                    streak = StreakCalculator.compute(habit, own, today).current,
                )
            }
            WeeklyOverview(summary, today, firstDay, rows)
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<UiState<WeeklyOverview>> = source.state

    fun retry() = source.retry()
}
