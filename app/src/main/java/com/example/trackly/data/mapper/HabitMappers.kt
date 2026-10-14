package com.example.trackly.data.mapper

import com.example.trackly.core.util.toEnumOrDefault
import com.example.trackly.data.local.entity.HabitCompletionEntity
import com.example.trackly.data.local.entity.HabitEntity
import com.example.trackly.data.local.entity.HabitPauseEntity
import com.example.trackly.data.local.entity.HabitReminderEntity
import com.example.trackly.data.local.entity.HabitScheduleEntity
import com.example.trackly.data.local.entity.HabitWithDetails
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.PauseRange
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.ReminderRepeat
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.domain.model.TimeOfDay
import java.time.LocalDate
import java.time.LocalTime

private const val SECONDS_PER_MINUTE = 60L

fun HabitWithDetails.toDomain(): Habit {
    val scheduleEntity = schedule
    val schedule = if (scheduleEntity == null) {
        HabitSchedule.Daily
    } else {
        HabitSchedule(
            type = scheduleEntity.type.toEnumOrDefault(ScheduleType.EVERY_DAY),
            days = HabitSchedule.maskToDays(scheduleEntity.daysMask),
            intervalDays = scheduleEntity.intervalDays.coerceAtLeast(1),
        )
    }
    return Habit(
        id = habit.id,
        name = habit.name,
        icon = habit.icon,
        colorArgb = habit.colorArgb,
        type = habit.type.toEnumOrDefault(HabitType.BOOLEAN),
        goalValue = habit.goalValue,
        unit = habit.unit.toEnumOrDefault(HabitUnit.TIMES),
        customUnit = habit.customUnit,
        stepValue = habit.stepValue,
        schedule = schedule,
        timeOfDay = habit.timeOfDay.toEnumOrDefault(TimeOfDay.ANYTIME),
        startDate = LocalDate.ofEpochDay(habit.startEpochDay),
        isArchived = habit.isArchived,
        sortOrder = habit.sortOrder,
        createdAt = habit.createdAt,
        updatedAt = habit.updatedAt,
        reminders = reminders.map { it.toDomain() }.sortedBy { it.time },
        pauses = pauses.map { it.toDomain() }.sortedBy { it.from },
    )
}

fun HabitReminderEntity.toDomain() = Reminder(
    id = id,
    habitId = habitId,
    time = LocalTime.ofSecondOfDay((minuteOfDay.coerceIn(0, 24 * 60 - 1)) * SECONDS_PER_MINUTE),
    message = message,
    enabled = enabled,
    repeat = repeatRule.toEnumOrDefault(ReminderRepeat.HABIT_SCHEDULE),
)

fun HabitPauseEntity.toDomain() = PauseRange(
    from = LocalDate.ofEpochDay(fromEpochDay),
    to = toEpochDay?.let { LocalDate.ofEpochDay(it) },
)

fun HabitCompletionEntity.toDomain() = HabitEntry(
    habitId = habitId,
    date = LocalDate.ofEpochDay(epochDay),
    value = value,
    goal = goal,
    completed = completed,
    updatedAt = updatedAt,
)

fun HabitDraft.toEntity(sortOrder: Int, createdAt: Long, updatedAt: Long, isArchived: Boolean = false) = HabitEntity(
    id = id,
    name = name,
    icon = icon,
    colorArgb = colorArgb,
    type = type.name,
    goalValue = goalValue,
    unit = unit.name,
    customUnit = customUnit,
    stepValue = stepValue,
    timeOfDay = timeOfDay.name,
    startEpochDay = startDate.toEpochDay(),
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun HabitSchedule.toEntity(habitId: Long) = HabitScheduleEntity(
    habitId = habitId,
    type = type.name,
    daysMask = HabitSchedule.daysToMask(days),
    intervalDays = intervalDays,
)

fun Reminder.toEntity(habitId: Long) = HabitReminderEntity(
    id = id,
    habitId = habitId,
    minuteOfDay = time.hour * 60 + time.minute,
    message = message?.takeIf { it.isNotBlank() },
    enabled = enabled,
    repeatRule = repeat.name,
)
