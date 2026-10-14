package com.example.trackly.fakes

import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.PauseRange
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.TimeOfDay
import java.time.LocalDate

object Fixtures {
    /** A Wednesday. */
    val TODAY: LocalDate = LocalDate.of(2026, 10, 14)

    fun habit(
        id: Long = 1,
        name: String = "Water",
        type: HabitType = HabitType.COUNTER,
        goal: Double = 8.0,
        unit: HabitUnit = HabitUnit.GLASSES,
        schedule: HabitSchedule = HabitSchedule.Daily,
        start: LocalDate = TODAY.minusDays(60),
        pauses: List<PauseRange> = emptyList(),
        reminders: List<Reminder> = emptyList(),
        archived: Boolean = false,
        color: Int = HabitColors.FREE[0],
        sortOrder: Int = 0,
    ) = Habit(
        id = id,
        name = name,
        icon = "💧",
        colorArgb = color,
        type = type,
        goalValue = goal,
        unit = unit,
        customUnit = null,
        stepValue = 1.0,
        schedule = schedule,
        timeOfDay = TimeOfDay.ANYTIME,
        startDate = start,
        isArchived = archived,
        sortOrder = sortOrder,
        createdAt = 0L,
        updatedAt = 0L,
        reminders = reminders,
        pauses = pauses,
    )

    fun entry(habitId: Long, date: LocalDate, value: Double, goal: Double = 8.0) = HabitEntry(
        habitId = habitId,
        date = date,
        value = value,
        goal = goal,
        completed = GoalProgress.isComplete(value, goal),
    )

    /** A completed entry for a goal-of-8 habit. */
    fun done(habitId: Long, date: LocalDate) = entry(habitId, date, 8.0, 8.0)

    fun doneOn(habitId: Long, vararg dates: LocalDate) = dates.map { done(habitId, it) }
}
