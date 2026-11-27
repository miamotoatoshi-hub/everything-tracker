package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class HabitMonthRate(val habit: Habit, val due: Int, val done: Int) {
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
}

data class MonthlyReport(
    val month: YearMonth,
    /** Goal-days due in the month (up to today) and how many of them were completed. */
    val due: Int,
    val done: Int,
    /** Rate of the previous month, or null when nothing was due then. */
    val previousRate: Float?,
    val perfectDays: Int,
    val activeDays: Int,
    val bestStreak: Int,
    val bestStreakHabit: Habit?,
    /** Habits that were due this month, best rate first. */
    val habits: List<HabitMonthRate>,
    val weekdayRates: Map<DayOfWeek, Float>,
    val bestWeekday: DayOfWeek?,
) {
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
    val isEmpty: Boolean get() = due == 0
}

/** Premium reports: a month in review and the year in pixels. Pure functions over the stored history. */
object Reports {

    fun month(habits: List<Habit>, entries: List<HabitEntry>, month: YearMonth, today: LocalDate): MonthlyReport {
        val active = habits.filter { !it.isArchived }
        val doneDates = doneDatesByHabit(entries)
        val first = month.atDay(1)
        val last = minOf(month.atEndOfMonth(), today)

        var due = 0
        var done = 0
        val weekdayDue = IntArray(7)
        val weekdayDone = IntArray(7)
        val perHabit = ArrayList<HabitMonthRate>()
        var bestStreak = 0
        var bestStreakHabit: Habit? = null

        for (habit in active) {
            val own = doneDates[habit.id].orEmpty()
            var habitDue = 0
            var habitDone = 0
            var day = first
            while (!day.isAfter(last)) {
                if (habit.isActiveOn(day)) {
                    val index = day.dayOfWeek.value - 1
                    habitDue++
                    weekdayDue[index]++
                    if (day in own) {
                        habitDone++
                        weekdayDone[index]++
                    }
                }
                day = day.plusDays(1)
            }
            if (habitDue > 0) {
                perHabit += HabitMonthRate(habit, habitDue, habitDone)
                due += habitDue
                done += habitDone
                val streak = StreakCalculator.compute(
                    from = maxOf(first, habit.startDate),
                    today = last,
                    isScheduled = habit::isActiveOn,
                    isDone = { it in own },
                ).best
                if (streak > bestStreak) {
                    bestStreak = streak
                    bestStreakHabit = habit
                }
            }
        }

        val inMonth = entries.filter { it.completed && !it.date.isBefore(first) && !it.date.isAfter(last) }
        val weekdayRates = DayOfWeek.values()
            .filter { weekdayDue[it.value - 1] > 0 }
            .associateWith { weekdayDone[it.value - 1].toFloat() / weekdayDue[it.value - 1] }
        // A weekday needs a few due goals before it can be called "best".
        val bestWeekday = DayOfWeek.values()
            .filter { weekdayDue[it.value - 1] >= MIN_WEEKDAY_SAMPLE }
            .maxByOrNull { weekdayDone[it.value - 1].toFloat() / weekdayDue[it.value - 1] }

        return MonthlyReport(
            month = month,
            due = due,
            done = done,
            previousRate = previousRate(active, doneDates, month.minusMonths(1), today),
            perfectDays = if (last.isBefore(first)) 0 else Achievements.perfectDays(active, inMonth, last),
            activeDays = inMonth.mapTo(HashSet()) { it.date }.size,
            bestStreak = bestStreak,
            bestStreakHabit = bestStreakHabit,
            habits = perHabit.sortedWith(compareByDescending<HabitMonthRate> { it.rate }.thenByDescending { it.done }),
            weekdayRates = weekdayRates,
            bestWeekday = bestWeekday,
        )
    }

    /**
     * Completion rate (0..1) of every day of [year] up to [today] on which at least one habit was due.
     * Days with nothing due are absent from the map.
     */
    fun yearPixels(habits: List<Habit>, entries: List<HabitEntry>, year: Int, today: LocalDate): Map<LocalDate, Float> {
        val active = habits.filter { !it.isArchived }
        val doneDates = doneDatesByHabit(entries)
        val result = HashMap<LocalDate, Float>()
        var day = LocalDate.of(year, 1, 1)
        val end = minOf(LocalDate.of(year, 12, 31), today)
        while (!day.isAfter(end)) {
            var due = 0
            var done = 0
            for (habit in active) {
                if (habit.isActiveOn(day)) {
                    due++
                    if (day in doneDates[habit.id].orEmpty()) done++
                }
            }
            if (due > 0) result[day] = done.toFloat() / due
            day = day.plusDays(1)
        }
        return result
    }

    private fun previousRate(
        active: List<Habit>,
        doneDates: Map<Long, Set<LocalDate>>,
        month: YearMonth,
        today: LocalDate,
    ): Float? {
        val first = month.atDay(1)
        val last = minOf(month.atEndOfMonth(), today)
        var due = 0
        var done = 0
        for (habit in active) {
            val own = doneDates[habit.id].orEmpty()
            var day = first
            while (!day.isAfter(last)) {
                if (habit.isActiveOn(day)) {
                    due++
                    if (day in own) done++
                }
                day = day.plusDays(1)
            }
        }
        return if (due == 0) null else done.toFloat() / due
    }

    private fun doneDatesByHabit(entries: List<HabitEntry>): Map<Long, Set<LocalDate>> {
        val map = HashMap<Long, MutableSet<LocalDate>>()
        for (entry in entries) if (entry.completed) map.getOrPut(entry.habitId) { HashSet() }.add(entry.date)
        return map
    }

    private const val MIN_WEEKDAY_SAMPLE = 2
}
