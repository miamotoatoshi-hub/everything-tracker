package com.example.trackly.presentation.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.DateRange
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.logic.HabitPeriodStats
import com.example.trackly.domain.logic.StatsCalculator
import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.logic.StreakInfo
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Completion rate of a slice of time (a week inside a month, or a month inside a year). */
data class RateBucket(val start: LocalDate, val rate: Float, val eligible: Int, val completed: Int)

data class HabitDetails(
    val habit: Habit,
    val today: LocalDate,
    val todayValue: Double,
    val todayProgress: Float,
    val todayCompleted: Boolean,
    val streak: StreakInfo,
    val totalCompletions: Int,
    val allTimeRate: Float,
    val byDate: Map<LocalDate, HabitEntry>,
    val week: HabitPeriodStats,
    val month: HabitPeriodStats,
    val year: HabitPeriodStats,
    val monthWeeks: List<RateBucket>,
    val yearMonths: List<RateBucket>,
    val firstDay: DayOfWeek,
    val policy: PremiumPolicy,
)

sealed interface DetailsEvent {
    data object Deleted : DetailsEvent
    data object Archived : DetailsEvent
    data class GoalReached(val habitName: String) : DetailsEvent
    data class RequiresPremium(val feature: PremiumFeature) : DetailsEvent
    data object Failed : DetailsEvent
}

class HabitDetailsViewModel(
    private val habitId: Long,
    private val habits: HabitRepository,
    private val todayProvider: TodayProvider,
    settings: SettingsRepository,
    private val policy: StateFlow<PremiumPolicy>,
) : ViewModel() {

    private val _visibleMonth = MutableStateFlow(YearMonth.from(todayProvider.current()))
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _events = MutableSharedFlow<DetailsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DetailsEvent> = _events.asSharedFlow()

    private val source = RetryableState<HabitDetails?>(viewModelScope) {
        combine(
            habits.observeHabit(habitId),
            habits.observeEntries(habitId),
            todayProvider.today,
            settings.settings,
            policy,
        ) { habit, entries, today, appSettings, currentPolicy ->
            if (habit == null) null else buildDetails(habit, entries, today, appSettings.firstDayOfWeek.resolve(Locale.getDefault()), currentPolicy)
        }.flowOn(Dispatchers.Default)
    }

    val uiState: StateFlow<UiState<HabitDetails?>> = source.state

    fun retry() = source.retry()

    // region navigation of the calendar

    fun showPreviousMonth() {
        val target = _visibleMonth.value.minusMonths(1)
        val earliest = policy.value.earliestCalendarMonth(todayProvider.current())
        if (earliest != null && target.isBefore(earliest)) {
            _events.tryEmit(DetailsEvent.RequiresPremium(PremiumFeature.ADVANCED_CALENDAR))
        } else {
            _visibleMonth.value = target
        }
    }

    fun showNextMonth() {
        val target = _visibleMonth.value.plusMonths(1)
        if (!target.isAfter(YearMonth.from(todayProvider.current()))) _visibleMonth.value = target
    }

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    // endregion

    // region logging

    fun onToggleBoolean(details: HabitDetails) = log(details) {
        habits.setEntryValue(habitId, details.today, if (details.todayCompleted) 0.0 else 1.0)
    }

    fun onAdd(details: HabitDetails, direction: Int) = log(details) {
        habits.addToEntry(habitId, details.today, details.habit.stepValue * direction)
    }

    fun onSetValue(details: HabitDetails, date: LocalDate, value: Double) = log(details, date) {
        habits.setEntryValue(habitId, date, value)
    }

    private fun log(details: HabitDetails, date: LocalDate = details.today, block: suspend () -> HabitEntry?) {
        launchSafely(onError = { _events.emit(DetailsEvent.Failed) }) {
            val wasComplete = details.byDate[date]?.completed == true
            val result = block()
            if (!wasComplete && result?.completed == true) {
                _events.emit(DetailsEvent.GoalReached(details.habit.name))
            }
        }
    }

    // endregion

    // region management

    fun setPaused(paused: Boolean) {
        launchSafely(onError = { _events.emit(DetailsEvent.Failed) }) {
            habits.setPaused(habitId, paused, todayProvider.current())
        }
    }

    fun setArchived(archived: Boolean) {
        launchSafely(onError = { _events.emit(DetailsEvent.Failed) }) {
            if (!archived && !policy.value.canCreateHabit(habits.countNonArchived())) {
                _events.emit(DetailsEvent.RequiresPremium(PremiumFeature.UNLIMITED_HABITS))
                return@launchSafely
            }
            habits.setArchived(habitId, archived)
            if (archived) _events.emit(DetailsEvent.Archived)
        }
    }

    fun delete() {
        launchSafely(onError = { _events.emit(DetailsEvent.Failed) }) {
            habits.deleteHabit(habitId)
            _events.emit(DetailsEvent.Deleted)
        }
    }

    // endregion

    private fun buildDetails(
        habit: Habit,
        entries: List<HabitEntry>,
        today: LocalDate,
        firstDay: DayOfWeek,
        policy: PremiumPolicy,
    ): HabitDetails {
        val byDate = entries.associateBy { it.date }
        val todayEntry = byDate[today]
        val value = todayEntry?.value ?: 0.0
        val streak = StreakCalculator.compute(habit, entries, today)

        val week = StatsCalculator.habitStats(habit, byDate, DateRange.week(today, firstDay), today)
        val month = StatsCalculator.habitStats(habit, byDate, DateRange.month(YearMonth.from(today)), today)
        val year = StatsCalculator.habitStats(habit, byDate, DateRange.year(today.year), today)

        val allTime = if (habit.startDate.isAfter(today)) {
            null
        } else {
            StatsCalculator.habitStats(habit, byDate, DateRange(habit.startDate, today), today)
        }

        val monthWeeks = weekBuckets(month, firstDay)
        val yearMonths = (1..12).map { m ->
            val stats = StatsCalculator.habitStats(habit, byDate, DateRange.month(YearMonth.of(today.year, m)), today)
            RateBucket(LocalDate.of(today.year, m, 1), stats.completionRate, stats.eligibleDays, stats.completedDays)
        }

        return HabitDetails(
            habit = habit,
            today = today,
            todayValue = value,
            todayProgress = GoalProgress.fraction(value, habit.goalValue),
            todayCompleted = todayEntry?.completed == true,
            streak = streak,
            totalCompletions = entries.count { it.completed },
            allTimeRate = allTime?.completionRate ?: 0f,
            byDate = byDate,
            week = week,
            month = month,
            year = year,
            monthWeeks = monthWeeks,
            yearMonths = yearMonths,
            firstDay = firstDay,
            policy = policy,
        )
    }

    private fun weekBuckets(month: HabitPeriodStats, firstDay: DayOfWeek): List<RateBucket> {
        val groups = LinkedHashMap<LocalDate, MutableList<com.example.trackly.domain.logic.DayCell>>()
        for (cell in month.cells) {
            val weekStart = DateRange.week(cell.date, firstDay).start
            groups.getOrPut(weekStart) { ArrayList() }.add(cell)
        }
        return groups.map { (start, cells) ->
            val eligible = cells.count { it.state == DayState.COMPLETED || it.state == DayState.PARTIAL || it.state == DayState.MISSED }
            val completed = cells.count { it.state == DayState.COMPLETED }
            RateBucket(start, if (eligible == 0) 0f else completed.toFloat() / eligible, eligible, completed)
        }
    }
}
