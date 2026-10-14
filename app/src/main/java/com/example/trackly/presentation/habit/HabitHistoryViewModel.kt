package com.example.trackly.presentation.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.logic.DayStateResolver
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.launchSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate

data class HistoryRow(
    val date: LocalDate,
    val state: DayState,
    val value: Double,
    val goal: Double,
)

data class HistoryData(
    val habit: Habit,
    val today: LocalDate,
    val rows: List<HistoryRow>,
    /** Older rows that exist but are hidden on the free plan. */
    val hiddenCount: Int,
)

sealed interface HistoryEvent {
    data object Failed : HistoryEvent
}

class HabitHistoryViewModel(
    private val habitId: Long,
    private val habits: HabitRepository,
    private val todayProvider: TodayProvider,
    policy: StateFlow<PremiumPolicy>,
) : ViewModel() {

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

    private val source = RetryableState<HistoryData?>(viewModelScope) {
        combine(habits.observeHabit(habitId), habits.observeEntries(habitId), todayProvider.today, policy) { habit, entries, today, currentPolicy ->
            if (habit == null) null else build(habit, entries, today, currentPolicy)
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<UiState<HistoryData?>> = source.state

    fun retry() = source.retry()

    fun setValue(date: LocalDate, value: Double) {
        launchSafely(onError = { _events.emit(HistoryEvent.Failed) }) {
            habits.setEntryValue(habitId, date, value)
        }
    }

    private fun build(habit: Habit, entries: List<HabitEntry>, today: LocalDate, policy: PremiumPolicy): HistoryData {
        val byDate = entries.associateBy { it.date }
        val all = ArrayList<HistoryRow>()
        var date = today
        while (!date.isBefore(habit.startDate)) {
            val entry = byDate[date]
            if (entry != null || habit.isActiveOn(date)) {
                all.add(
                    HistoryRow(
                        date = date,
                        state = DayStateResolver.resolve(habit, entry, date, today),
                        value = entry?.value ?: 0.0,
                        goal = entry?.goal ?: habit.goalValue,
                    ),
                )
            }
            date = date.minusDays(1)
        }
        // Entries logged before the start date (the user moved it later) still belong in the history.
        entries.filter { it.date.isBefore(habit.startDate) }.sortedByDescending { it.date }.forEach { entry ->
            all.add(
                HistoryRow(
                    date = entry.date,
                    state = DayStateResolver.resolve(habit, entry, entry.date, today),
                    value = entry.value,
                    goal = entry.goal,
                ),
            )
        }
        val cutoff = policy.earliestHistoryDate(today)
        val visible = if (cutoff == null) all else all.filter { !it.date.isBefore(cutoff) }
        return HistoryData(habit, today, visible, all.size - visible.size)
    }

    companion object {
        val lockedFeature = PremiumFeature.FULL_HISTORY
    }
}
