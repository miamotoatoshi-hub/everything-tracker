package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.ReminderRepeat
import java.time.LocalDateTime

object ReminderTimeCalculator {
    private const val SEARCH_DAYS = 400

    /**
     * The first moment strictly after [from] at which [reminder] should fire for [habit],
     * or null when it never will (disabled, archived, or the habit has no future active day).
     */
    fun nextTrigger(habit: Habit, reminder: Reminder, from: LocalDateTime): LocalDateTime? {
        if (!reminder.enabled || habit.isArchived) return null
        var date = from.toLocalDate()
        repeat(SEARCH_DAYS) {
            val eligible = when (reminder.repeat) {
                ReminderRepeat.EVERY_DAY -> !date.isBefore(habit.startDate) && !habit.isPausedOn(date)
                ReminderRepeat.HABIT_SCHEDULE -> habit.isActiveOn(date)
            }
            if (eligible) {
                val candidate = LocalDateTime.of(date, reminder.time)
                if (candidate.isAfter(from)) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }
}
