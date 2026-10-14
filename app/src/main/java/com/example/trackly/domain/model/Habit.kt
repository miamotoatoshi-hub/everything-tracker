package com.example.trackly.domain.model

import java.time.LocalDate

/** An inclusive span during which a habit was paused. [to] == null means "still paused". */
data class PauseRange(val from: LocalDate, val to: LocalDate?) {
    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(from) && (to == null || !date.isAfter(to))
}

data class Habit(
    val id: Long,
    val name: String,
    /** An emoji, e.g. "💧". */
    val icon: String,
    val colorArgb: Int,
    val type: HabitType,
    val goalValue: Double,
    val unit: HabitUnit,
    /** Only meaningful when [unit] is [HabitUnit.CUSTOM]. */
    val customUnit: String?,
    /** Increment applied by the quick "+" button. */
    val stepValue: Double,
    val schedule: HabitSchedule,
    val timeOfDay: TimeOfDay,
    val startDate: LocalDate,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val reminders: List<Reminder> = emptyList(),
    val pauses: List<PauseRange> = emptyList(),
) {
    val isPaused: Boolean get() = pauses.any { it.to == null }

    fun isPausedOn(date: LocalDate): Boolean = pauses.any { date in it }

    /** True when the habit is expected to be done on [date] (scheduled, started and not paused). */
    fun isActiveOn(date: LocalDate): Boolean =
        schedule.isScheduledOn(date, startDate) && !isPausedOn(date)
}

/** Everything the user edits in the habit form. [id] == 0 means "new habit". */
data class HabitDraft(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val colorArgb: Int,
    val type: HabitType,
    val goalValue: Double,
    val unit: HabitUnit,
    val customUnit: String? = null,
    val stepValue: Double,
    val schedule: HabitSchedule = HabitSchedule.Daily,
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
    val startDate: LocalDate,
    val reminders: List<Reminder> = emptyList(),
) {
    companion object {
        fun from(habit: Habit) = HabitDraft(
            id = habit.id,
            name = habit.name,
            icon = habit.icon,
            colorArgb = habit.colorArgb,
            type = habit.type,
            goalValue = habit.goalValue,
            unit = habit.unit,
            customUnit = habit.customUnit,
            stepValue = habit.stepValue,
            schedule = habit.schedule,
            timeOfDay = habit.timeOfDay,
            startDate = habit.startDate,
            reminders = habit.reminders,
        )
    }
}
