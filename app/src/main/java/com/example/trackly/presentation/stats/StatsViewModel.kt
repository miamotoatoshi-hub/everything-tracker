package com.example.trackly.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.domain.logic.DateRange
import com.example.trackly.domain.logic.DateUtils
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.logic.HabitRate
import com.example.trackly.domain.logic.Insights
import com.example.trackly.domain.logic.StatsCalculator
import com.example.trackly.domain.logic.StatsPeriod
import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
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
import java.util.Locale

/** One bar: a day (week view), a week (month view) or a month (year view). */
data class StatBucket(val start: LocalDate, val fraction: Float, val eligible: Int, val isCurrent: Boolean, val isFuture: Boolean)

data class StatsData(
    val habits: List<Habit>,
    /** null = overall. */
    val selectedHabit: Habit?,
    val period: StatsPeriod,
    val today: LocalDate,
    val firstDay: DayOfWeek,
    val range: DateRange,
    val locked: Boolean,
    val completionRate: Float,
    val totalCompletions: Int,
    val averageProgress: Float,
    val currentStreak: Int,
    val bestStreak: Int,
    val perfectDays: Int,
    val buckets: List<StatBucket>,
    val ranking: List<HabitRate>,
    val insights: Insights?,
    val policy: PremiumPolicy,
)

sealed interface StatsEvent {
    data class RequiresPremium(val feature: PremiumFeature) : StatsEvent
}

private data class StatsSelection(val habitId: Long? = null, val period: StatsPeriod = StatsPeriod.WEEK)

class StatsViewModel(
    habits: HabitRepository,
    private val todayProvider: TodayProvider,
    settings: SettingsRepository,
    private val policy: StateFlow<PremiumPolicy>,
) : ViewModel() {

    private val selection = MutableStateFlow(StatsSelection())

    private val _events = MutableSharedFlow<StatsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StatsEvent> = _events.asSharedFlow()

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

    val uiState: StateFlow<UiState<StatsData>> = source.state

    fun retry() = source.retry()

    fun selectHabit(id: Long?) = selection.update { it.copy(habitId = id) }

    fun selectPeriod(period: StatsPeriod) {
        if (!policy.value.isStatsPeriodAllowed(period)) {
            // Still show the locked preview for the chosen period, and let the UI offer the upgrade.
            _events.tryEmit(StatsEvent.RequiresPremium(PremiumFeature.ADVANCED_STATISTICS))
        }
        selection.update { it.copy(period = period) }
    }

    private class Source(
        val habits: List<Habit>,
        val entries: List<HabitEntry>,
        val today: LocalDate,
        val firstDay: DayOfWeek,
        val policy: PremiumPolicy,
    )

    private data class Day(val date: LocalDate, val eligible: Int, val completed: Int, val fraction: Float)

    private fun build(src: Source, sel: StatsSelection): StatsData {
        val byHabit = src.entries.groupBy { it.habitId }.mapValues { (_, list) -> list.associateBy { it.date } }
        val empty = emptyMap<LocalDate, HabitEntry>()
        val selected = sel.habitId?.let { id -> src.habits.firstOrNull { it.id == id } }
        val range = DateUtils.rangeFor(sel.period, src.today, src.firstDay)
        val locked = !src.policy.isStatsPeriodAllowed(sel.period)

        val days: List<Day>
        val rate: Float
        val completions: Int
        val average: Float
        val perfect: Int
        val currentStreak: Int
        val bestStreak: Int

        if (selected != null) {
            val own = byHabit[selected.id] ?: empty
            val stats = StatsCalculator.habitStats(selected, own, range, src.today)
            days = stats.cells.map { cell ->
                val counts = cell.state == DayState.COMPLETED || cell.state == DayState.PARTIAL || cell.state == DayState.MISSED
                Day(
                    date = cell.date,
                    eligible = if (counts) 1 else 0,
                    completed = if (cell.state == DayState.COMPLETED) 1 else 0,
                    fraction = GoalProgress.fraction(cell.value, cell.goal),
                )
            }
            rate = stats.completionRate
            completions = own.values.count { it.completed && it.date in range }
            average = stats.averageProgress
            perfect = stats.completedDays
            val streak = StreakCalculator.compute(selected, own.values, src.today)
            currentStreak = streak.current
            bestStreak = streak.best
        } else {
            val overall = StatsCalculator.overall(src.habits, byHabit, range, src.today)
            days = overall.days.map { agg ->
                val past = agg.date.isBefore(src.today)
                val eligible = if (past) agg.scheduled else if (agg.date == src.today) agg.completed else 0
                Day(agg.date, eligible, agg.completed, agg.fraction)
            }
            rate = overall.completionRate
            completions = src.entries.count { it.completed && it.date in range }
            average = if (days.isEmpty()) 0f else {
                val eligibleDays = days.filter { it.eligible > 0 }
                if (eligibleDays.isEmpty()) 0f else eligibleDays.map { it.fraction }.average().toFloat()
            }
            perfect = overall.perfectDays
            val streaks = src.habits.map { StreakCalculator.compute(it, (byHabit[it.id] ?: empty).values, src.today) }
            currentStreak = streaks.maxOfOrNull { it.current } ?: 0
            bestStreak = streaks.maxOfOrNull { it.best } ?: 0
        }

        val ranking = if (selected == null) {
            src.habits.map { habit ->
                val s = StatsCalculator.habitStats(habit, byHabit[habit.id] ?: empty, range, src.today)
                HabitRate(habit, s.completionRate, s.completedDays, s.eligibleDays)
            }.sortedByDescending { it.rate }
        } else {
            emptyList()
        }

        val insights = if (src.policy.isUnlocked(PremiumFeature.PREMIUM_INSIGHTS) && selected == null) {
            StatsCalculator.insights(src.habits, src.entries, src.today)
        } else {
            null
        }

        return StatsData(
            habits = src.habits,
            selectedHabit = selected,
            period = sel.period,
            today = src.today,
            firstDay = src.firstDay,
            range = range,
            locked = locked,
            completionRate = rate,
            totalCompletions = completions,
            averageProgress = average,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            perfectDays = perfect,
            buckets = bucketize(sel.period, days, src.today, src.firstDay, useProgress = sel.period == StatsPeriod.WEEK),
            ranking = ranking,
            insights = insights,
            policy = src.policy,
        )
    }

    private fun bucketize(
        period: StatsPeriod,
        days: List<Day>,
        today: LocalDate,
        firstDay: DayOfWeek,
        useProgress: Boolean,
    ): List<StatBucket> {
        val groups = LinkedHashMap<LocalDate, MutableList<Day>>()
        for (day in days) {
            val key = when (period) {
                StatsPeriod.WEEK -> day.date
                StatsPeriod.MONTH -> DateRange.week(day.date, firstDay).start
                StatsPeriod.YEAR -> day.date.withDayOfMonth(1)
            }
            groups.getOrPut(key) { ArrayList() }.add(day)
        }
        return groups.map { (start, list) ->
            val eligible = list.sumOf { it.eligible }
            val completed = list.sumOf { it.completed }
            val fraction = if (useProgress) {
                list.first().fraction
            } else if (eligible == 0) {
                0f
            } else {
                completed.toFloat() / eligible
            }
            val containsToday = list.any { it.date == today }
            StatBucket(
                start = start,
                fraction = fraction,
                eligible = eligible,
                isCurrent = containsToday,
                isFuture = list.all { it.date.isAfter(today) },
            )
        }
    }
}
