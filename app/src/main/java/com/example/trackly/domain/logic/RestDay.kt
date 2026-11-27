package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import java.time.LocalDate

/**
 * "Skip today": a one-day pause. Paused days neither extend nor break a streak (see [StreakCalculator]),
 * so a rest day keeps the streak without a new kind of data.
 */
object RestDay {

    fun isRestDay(habit: Habit, date: LocalDate): Boolean = habit.pauses.any { it.from == date && it.to == date }

    /** A rest day can be taken on a due, not yet completed day of a habit that is not paused anyway. */
    fun canTake(habit: Habit, date: LocalDate, completed: Boolean): Boolean =
        !completed && !habit.isPaused && !habit.isArchived && habit.isActiveOn(date)
}
