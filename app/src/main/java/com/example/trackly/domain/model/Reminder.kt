package com.example.trackly.domain.model

import java.time.LocalTime

enum class ReminderRepeat {
    /** Fire only on days the habit is scheduled (and not paused). */
    HABIT_SCHEDULE,

    /** Fire every day regardless of the habit schedule. */
    EVERY_DAY,
}

data class Reminder(
    val id: Long = 0,
    val habitId: Long = 0,
    val time: LocalTime,
    val message: String? = null,
    val enabled: Boolean = true,
    val repeat: ReminderRepeat = ReminderRepeat.HABIT_SCHEDULE,
)
