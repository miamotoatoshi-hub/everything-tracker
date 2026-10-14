package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import java.time.LocalDate

enum class DayState {
    /** Nothing expected on this day (not scheduled, paused, or before the start date). */
    NOT_SCHEDULED,

    /** Scheduled but still in the future. */
    FUTURE,

    /** Scheduled for today and not finished yet. */
    PENDING,

    /** Scheduled in the past with no (or zero) progress. */
    MISSED,

    /** Some progress, but the goal was not reached. */
    PARTIAL,

    /** Goal reached. */
    COMPLETED,
}

object DayStateResolver {
    fun resolve(habit: Habit, entry: HabitEntry?, date: LocalDate, today: LocalDate): DayState {
        val value = entry?.value ?: 0.0
        return when {
            entry != null && entry.completed -> DayState.COMPLETED
            entry != null && value > 0.0 -> DayState.PARTIAL
            !habit.isActiveOn(date) -> DayState.NOT_SCHEDULED
            date.isAfter(today) -> DayState.FUTURE
            date == today -> DayState.PENDING
            else -> DayState.MISSED
        }
    }

    /** State for a cell that aggregates several habits on one day. */
    fun resolveAggregate(scheduled: Int, completed: Int, partial: Int, date: LocalDate, today: LocalDate): DayState =
        when {
            scheduled == 0 && completed == 0 -> if (partial > 0) DayState.PARTIAL else DayState.NOT_SCHEDULED
            completed > 0 && completed >= scheduled -> DayState.COMPLETED
            completed > 0 || partial > 0 -> DayState.PARTIAL
            date.isAfter(today) -> DayState.FUTURE
            date == today -> DayState.PENDING
            else -> DayState.MISSED
        }
}
