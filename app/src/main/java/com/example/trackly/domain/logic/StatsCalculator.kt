package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import java.time.DayOfWeek
import java.time.LocalDate

/** One calendar cell for a single habit. */
data class DayCell(
    val date: LocalDate,
    val state: DayState,
    val value: Double,
    val goal: Double,
)

data class HabitPeriodStats(
    val range: DateRange,
    /** Days that count toward the rate: scheduled days in the past, plus today once completed. */
    val eligibleDays: Int,
    val completedDays: Int,
    val partialDays: Int,
    val missedDays: Int,
    /** completedDays / eligibleDays in 0..1 (0 when nothing is eligible yet). */
    val completionRate: Float,
    /** Mean progress toward the goal over eligible days, in 0..1. */
    val averageProgress: Float,
    val totalValue: Double,
    val cells: List<DayCell>,
)

/** All habits aggregated for one day. */
data class DayAggregate(
    val date: LocalDate,
    val scheduled: Int,
    val completed: Int,
    val partial: Int,
) {
    val fraction: Float get() = if (scheduled == 0) 0f else (completed.toFloat() / scheduled).coerceIn(0f, 1f)
}

data class OverallPeriodStats(
    val range: DateRange,
    val habitCount: Int,
    val eligibleSlots: Int,
    val completedSlots: Int,
    val completionRate: Float,
    val perfectDays: Int,
    val days: List<DayAggregate>,
)

data class WeeklySummary(
    val range: DateRange,
    val habitCount: Int,
    val completions: Int,
    val completionRate: Float,
    val bestStreak: Int,
    val days: List<DayAggregate>,
)

data class HabitRate(val habit: Habit, val rate: Float, val completed: Int, val eligible: Int)

data class Insights(
    val bestDay: DayOfWeek?,
    val weakestDay: DayOfWeek?,
    val topHabit: HabitRate?,
    /** Completion rate of the last 7 days minus the 7 days before, in -1..1. Null without enough data. */
    val weeklyTrend: Float?,
    val perfectDaysLast30: Int,
)

object StatsCalculator {

    fun habitStats(
        habit: Habit,
        entries: Collection<HabitEntry>,
        range: DateRange,
        today: LocalDate,
    ): HabitPeriodStats {
        val byDate = entries.associateBy { it.date }
        return habitStats(habit, byDate, range, today)
    }

    fun habitStats(
        habit: Habit,
        byDate: Map<LocalDate, HabitEntry>,
        range: DateRange,
        today: LocalDate,
    ): HabitPeriodStats {
        var eligible = 0
        var completed = 0
        var partial = 0
        var missed = 0
        var progressSum = 0f
        var total = 0.0
        val cells = ArrayList<DayCell>(range.length)
        for (date in range.days()) {
            val entry = byDate[date]
            val state = DayStateResolver.resolve(habit, entry, date, today)
            val value = entry?.value ?: 0.0
            val goal = entry?.goal ?: habit.goalValue
            cells.add(DayCell(date, state, value, goal))
            total += value
            val scheduled = habit.isActiveOn(date)
            val counts = scheduled && (date.isBefore(today) || (date == today && state == DayState.COMPLETED))
            if (counts) {
                eligible++
                progressSum += GoalProgress.fraction(value, goal)
                when (state) {
                    DayState.COMPLETED -> completed++
                    DayState.PARTIAL -> partial++
                    else -> missed++
                }
            }
        }
        return HabitPeriodStats(
            range = range,
            eligibleDays = eligible,
            completedDays = completed,
            partialDays = partial,
            missedDays = missed,
            completionRate = if (eligible == 0) 0f else completed.toFloat() / eligible,
            averageProgress = if (eligible == 0) 0f else progressSum / eligible,
            totalValue = total,
            cells = cells,
        )
    }

    fun overall(
        habits: List<Habit>,
        entries: Collection<HabitEntry>,
        range: DateRange,
        today: LocalDate,
    ): OverallPeriodStats {
        val byHabit = entries.groupBy { it.habitId }.mapValues { (_, list) -> list.associateBy { it.date } }
        return overall(habits, byHabit, range, today)
    }

    fun overall(
        habits: List<Habit>,
        byHabit: Map<Long, Map<LocalDate, HabitEntry>>,
        range: DateRange,
        today: LocalDate,
    ): OverallPeriodStats {
        var eligibleSlots = 0
        var completedSlots = 0
        var perfectDays = 0
        val days = ArrayList<DayAggregate>(range.length)
        val empty = emptyMap<LocalDate, HabitEntry>()
        for (date in range.days()) {
            var scheduled = 0
            var completed = 0
            var partial = 0
            for (habit in habits) {
                val entry = (byHabit[habit.id] ?: empty)[date]
                val active = habit.isActiveOn(date)
                if (active) scheduled++
                val isDone = entry != null && entry.completed
                if (isDone && active) completed++
                if (!isDone && entry != null && entry.value > 0.0) partial++
                val counts = active && (date.isBefore(today) || (date == today && isDone))
                if (counts) {
                    eligibleSlots++
                    if (isDone) completedSlots++
                }
            }
            if (scheduled > 0 && completed == scheduled && !date.isAfter(today)) perfectDays++
            days.add(DayAggregate(date, scheduled, completed, partial))
        }
        return OverallPeriodStats(
            range = range,
            habitCount = habits.size,
            eligibleSlots = eligibleSlots,
            completedSlots = completedSlots,
            completionRate = if (eligibleSlots == 0) 0f else completedSlots.toFloat() / eligibleSlots,
            perfectDays = perfectDays,
            days = days,
        )
    }

    fun weeklySummary(
        habits: List<Habit>,
        entries: Collection<HabitEntry>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): WeeklySummary {
        val range = DateRange.week(today, firstDayOfWeek)
        val stats = overall(habits, entries, range, today)
        val byHabit = entries.groupBy { it.habitId }
        val completions = entries.count { it.completed && it.date in range }
        val best = habits.maxOfOrNull { StreakCalculator.compute(it, byHabit[it.id].orEmpty(), today).best } ?: 0
        return WeeklySummary(
            range = range,
            habitCount = habits.size,
            completions = completions,
            completionRate = stats.completionRate,
            bestStreak = best,
            days = stats.days,
        )
    }

    fun insights(habits: List<Habit>, entries: Collection<HabitEntry>, today: LocalDate): Insights {
        val byHabit = entries.groupBy { it.habitId }.mapValues { (_, list) -> list.associateBy { it.date } }

        // Best / weakest weekday over the last 8 weeks.
        val window = DateRange.lastDays(56, today)
        val eligibleByDow = IntArray(8)
        val doneByDow = IntArray(8)
        val empty = emptyMap<LocalDate, HabitEntry>()
        for (date in window.days()) {
            for (habit in habits) {
                if (!habit.isActiveOn(date)) continue
                val done = (byHabit[habit.id] ?: empty)[date]?.completed == true
                if (date.isBefore(today) || (date == today && done)) {
                    eligibleByDow[date.dayOfWeek.value]++
                    if (done) doneByDow[date.dayOfWeek.value]++
                }
            }
        }
        val rated = DayOfWeek.entries
            .filter { eligibleByDow[it.value] >= MIN_SAMPLES }
            .map { it to doneByDow[it.value].toFloat() / eligibleByDow[it.value] }
        val best = rated.maxByOrNull { it.second }
        val weakest = rated.minByOrNull { it.second }

        // Most consistent habit over the last 30 days.
        val month = DateRange.lastDays(30, today)
        val topHabit = habits
            .map { habit ->
                val s = habitStats(habit, byHabit[habit.id] ?: empty, month, today)
                HabitRate(habit, s.completionRate, s.completedDays, s.eligibleDays)
            }
            .filter { it.eligible >= MIN_SAMPLES }
            .maxWithOrNull(compareBy<HabitRate> { it.rate }.thenBy { it.completed })

        // Trend: last 7 days vs the 7 days before.
        val lastWeek = DateRange.lastDays(7, today)
        val now = overall(habits, byHabit, lastWeek, today)
        val before = overall(habits, byHabit, lastWeek.previous(), today)
        val trend = if (now.eligibleSlots >= MIN_SAMPLES && before.eligibleSlots >= MIN_SAMPLES) {
            now.completionRate - before.completionRate
        } else {
            null
        }

        val perfect = overall(habits, byHabit, month, today).perfectDays

        return Insights(
            bestDay = best?.first,
            weakestDay = if (weakest != null && weakest.first != best?.first) weakest.first else null,
            topHabit = topHabit,
            weeklyTrend = trend,
            perfectDaysLast30 = perfect,
        )
    }

    private const val MIN_SAMPLES = 3
}
