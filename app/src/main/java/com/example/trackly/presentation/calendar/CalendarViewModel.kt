package com.example.trackly.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.DateRange
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.logic.DayStateResolver
import com.example.trackly.domain.logic.StatsCalculator
import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.launchSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

data class DayRow(
    val habit: Habit,
    val state: DayState,
    val value: Double,
    val goal: Double,
)

data class CalendarData(
    val habits: List<Habit>,
    /** null = "all habits". */
    val selectedHabit: Habit?,
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val month: YearMonth,
    val dayStates: Map<LocalDate, DayState>,
    val monthRate: Float,
    val monthCompleted: Int,
    val monthEligible: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val selectedDate: LocalDate?,
    val dayRows: List<DayRow>,
    val policy: PremiumPolicy,
) {
    val canGoNext: Boolean get() = month.isBefore(YearMonth.from(today))
    val previousLocked: Boolean
        get() {
            val earliest = policy.earliestCalendarMonth(today)
            return earliest != null && month.minusMonths(1).isBefore(earliest)
        }
}

sealed interface CalendarEvent {
    data class RequiresPremium(val feature: PremiumFeature) : CalendarEvent
    data object Failed : CalendarEvent
}

private data class CalendarSelection(
    val habitId: Long? = null,
    val month: YearMonth,
    val date: LocalDate? = null,
)

class CalendarViewModel(
    private val habits: HabitRepository,
    private val todayProvider: TodayProvider,
    settings: SettingsRepository,
    private val policy: StateFlow<PremiumPolicy>,
) : ViewModel() {

    private val selection = MutableStateFlow(CalendarSelection(month = YearMonth.from(todayProvider.current())))

    private val _events = MutableSharedFlow<CalendarEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CalendarEvent> = _events.asSharedFlow()

    private val source = RetryableState(viewModelScope) {
        val shared = combine(
            habits.observeHabits(),
            habits.observeEntries(),
            todayProvider.today,
            settings.settings,
            policy,
        ) { list, entries, today, appSettings, currentPolicy ->
            Source(list.filter { !it.isArchived }, entries, today, appSettings.firstDayOfWeek.resolve(Locale.getDefault()), currentPolicy)
        }
        combine(shared, selection) { src, sel -> build(src, sel) }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<UiState<CalendarData>> = source.state

    fun retry() = source.retry()

    fun selectHabit(id: Long?) = selection.update { it.copy(habitId = id, date = null) }

    fun selectDate(date: LocalDate) = selection.update { it.copy(date = if (it.date == date) null else date) }

    fun previousMonth() {
        val current = selection.value
        val target = current.month.minusMonths(1)
        val earliest = policy.value.earliestCalendarMonth(todayProvider.current())
        if (earliest != null && target.isBefore(earliest)) {
            _events.tryEmit(CalendarEvent.RequiresPremium(PremiumFeature.ADVANCED_CALENDAR))
        } else {
            selection.value = current.copy(month = target, date = null)
        }
    }

    fun nextMonth() {
        val current = selection.value
        val target = current.month.plusMonths(1)
        if (!target.isAfter(YearMonth.from(todayProvider.current()))) {
            selection.value = current.copy(month = target, date = null)
        }
    }

    fun setValue(habitId: Long, date: LocalDate, value: Double) {
        launchSafely(onError = { _events.emit(CalendarEvent.Failed) }) {
            habits.setEntryValue(habitId, date, value)
        }
    }

    private class Source(
        val habits: List<Habit>,
        val entries: List<HabitEntry>,
        val today: LocalDate,
        val firstDay: DayOfWeek,
        val policy: PremiumPolicy,
    )

    private fun build(src: Source, sel: CalendarSelection): CalendarData {
        val byHabit = src.entries.groupBy { it.habitId }.mapValues { (_, list) -> list.associateBy { it.date } }
        val selected = sel.habitId?.let { id -> src.habits.firstOrNull { it.id == id } }
        val range = DateRange.month(sel.month)
        val empty = emptyMap<LocalDate, HabitEntry>()

        val dayStates = HashMap<LocalDate, DayState>()
        val rate: Float
        val completed: Int
        val eligible: Int
        val current: Int
        val best: Int

        if (selected != null) {
            val own = byHabit[selected.id] ?: empty
            val stats = StatsCalculator.habitStats(selected, own, range, src.today)
            stats.cells.forEach { dayStates[it.date] = it.state }
            rate = stats.completionRate
            completed = stats.completedDays
            eligible = stats.eligibleDays
            val streak = StreakCalculator.compute(selected, own.values, src.today)
            current = streak.current
            best = streak.best
        } else {
            val overall = StatsCalculator.overall(src.habits, byHabit, range, src.today)
            overall.days.forEach {
                dayStates[it.date] = DayStateResolver.resolveAggregate(it.scheduled, it.completed, it.partial, it.date, src.today)
            }
            rate = overall.completionRate
            completed = overall.completedSlots
            eligible = overall.eligibleSlots
            val streaks = src.habits.map { StreakCalculator.compute(it, (byHabit[it.id] ?: empty).values, src.today) }
            current = streaks.maxOfOrNull { it.current } ?: 0
            best = streaks.maxOfOrNull { it.best } ?: 0
        }

        val scope = if (selected != null) listOf(selected) else src.habits
        val rows = sel.date?.let { date ->
            scope.mapNotNull { habit ->
                val entry = (byHabit[habit.id] ?: empty)[date]
                if (selected == null && entry == null && !habit.isActiveOn(date)) {
                    null
                } else {
                    DayRow(
                        habit = habit,
                        state = DayStateResolver.resolve(habit, entry, date, src.today),
                        value = entry?.value ?: 0.0,
                        goal = entry?.goal ?: habit.goalValue,
                    )
                }
            }
        }.orEmpty()

        return CalendarData(
            habits = src.habits,
            selectedHabit = selected,
            today = src.today,
            firstDay = src.firstDay,
            month = sel.month,
            dayStates = dayStates,
            monthRate = rate,
            monthCompleted = completed,
            monthEligible = eligible,
            currentStreak = current,
            bestStreak = best,
            selectedDate = sel.date,
            dayRows = rows,
            policy = src.policy,
        )
    }
}
