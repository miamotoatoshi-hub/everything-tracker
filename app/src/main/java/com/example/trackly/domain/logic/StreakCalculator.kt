package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import java.time.LocalDate

data class StreakInfo(val current: Int, val best: Int) {
    companion object {
        val None = StreakInfo(0, 0)
    }
}

/**
 * Schedule-aware streak engine.
 *
 *  - Only days the habit is scheduled on count. Unscheduled days (e.g. Tuesday for a
 *    Mon/Wed/Fri habit) neither extend nor break a streak.
 *  - A scheduled day in the past that was not completed resets the streak.
 *  - Today being scheduled but not yet completed does NOT break the streak — the user
 *    still has the rest of the day.
 */
object StreakCalculator {

    fun compute(habit: Habit, entries: Collection<HabitEntry>, today: LocalDate): StreakInfo {
        val done = HashSet<LocalDate>(entries.size)
        for (entry in entries) if (entry.completed) done.add(entry.date)
        return compute(
            from = habit.startDate,
            today = today,
            isScheduled = habit::isActiveOn,
            isDone = { it in done },
        )
    }

    fun compute(
        from: LocalDate,
        today: LocalDate,
        isScheduled: (LocalDate) -> Boolean,
        isDone: (LocalDate) -> Boolean,
    ): StreakInfo {
        var run = 0
        var best = 0
        var day = from
        while (!day.isAfter(today)) {
            if (isScheduled(day)) {
                if (isDone(day)) {
                    run++
                    if (run > best) best = run
                } else if (day != today) {
                    run = 0
                }
            }
            day = day.plusDays(1)
        }
        return StreakInfo(current = run, best = best)
    }
}
