package com.example.trackly.data

import com.example.trackly.data.local.entity.HabitEntity
import com.example.trackly.data.local.entity.HabitPauseEntity
import com.example.trackly.data.local.entity.HabitReminderEntity
import com.example.trackly.data.local.entity.HabitScheduleEntity
import com.example.trackly.data.local.entity.HabitWithDetails
import com.example.trackly.data.mapper.toDomain
import com.example.trackly.data.mapper.toEntity
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.ReminderRepeat
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.domain.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class HabitMappersTest {
    private val start = LocalDate.of(2026, 9, 1)

    private fun entity(
        type: String = "COUNTER",
        unit: String = "GLASSES",
        timeOfDay: String = "MORNING",
    ) = HabitEntity(
        id = 7, name = "Water", icon = "💧", colorArgb = HabitColors.FREE[5], type = type, goalValue = 8.0,
        unit = unit, customUnit = null, stepValue = 1.0, timeOfDay = timeOfDay, startEpochDay = start.toEpochDay(),
        isArchived = false, sortOrder = 2, createdAt = 10, updatedAt = 20,
    )

    @Test
    fun `draft round trips through entities`() {
        val draft = HabitDraft(
            name = "Run", icon = "🏃", colorArgb = HabitColors.FREE[0], type = HabitType.DISTANCE, goalValue = 2.5,
            unit = HabitUnit.KM, stepValue = 0.5,
            schedule = HabitSchedule(ScheduleType.SPECIFIC_DAYS, setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)),
            timeOfDay = TimeOfDay.EVENING, startDate = start,
            reminders = listOf(Reminder(time = LocalTime.of(18, 45), message = "Go!", repeat = ReminderRepeat.EVERY_DAY)),
        )
        val habitEntity = draft.toEntity(sortOrder = 3, createdAt = 1, updatedAt = 2).copy(id = 5)
        val details = HabitWithDetails(
            habit = habitEntity,
            schedule = draft.schedule.toEntity(5),
            reminders = draft.reminders.map { it.toEntity(5) },
            pauses = emptyList(),
        )

        val habit = details.toDomain()

        assertEquals("Run", habit.name)
        assertEquals(HabitType.DISTANCE, habit.type)
        assertEquals(2.5, habit.goalValue, 0.0)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), habit.schedule.days)
        assertEquals(TimeOfDay.EVENING, habit.timeOfDay)
        assertEquals(start, habit.startDate)
        assertEquals(3, habit.sortOrder)
        assertEquals(LocalTime.of(18, 45), habit.reminders.single().time)
        assertEquals(ReminderRepeat.EVERY_DAY, habit.reminders.single().repeat)
        assertEquals("Go!", habit.reminders.single().message)
    }

    @Test
    fun `corrupted enum values fall back to safe defaults instead of crashing`() {
        val details = HabitWithDetails(
            habit = entity(type = "???", unit = "", timeOfDay = "noon-ish"),
            schedule = HabitScheduleEntity(habitId = 7, type = "SOMETIMES", daysMask = 0, intervalDays = 0),
            reminders = listOf(
                HabitReminderEntity(habitId = 7, minuteOfDay = 99_999, message = "  ", enabled = true, repeatRule = "weird"),
            ),
            pauses = emptyList(),
        )

        val habit = details.toDomain()

        assertEquals(HabitType.BOOLEAN, habit.type)
        assertEquals(HabitUnit.TIMES, habit.unit)
        assertEquals(TimeOfDay.ANYTIME, habit.timeOfDay)
        assertEquals(ScheduleType.EVERY_DAY, habit.schedule.type)
        assertEquals(1, habit.schedule.intervalDays) // an interval below 1 is repaired
        // The out-of-range reminder time is clamped to the last minute of the day.
        assertEquals(LocalTime.of(23, 59), habit.reminders.single().time)
        assertEquals(ReminderRepeat.HABIT_SCHEDULE, habit.reminders.single().repeat)
    }

    @Test
    fun `a missing schedule row means every day`() {
        val habit = HabitWithDetails(entity(), schedule = null, reminders = emptyList(), pauses = emptyList()).toDomain()
        assertEquals(HabitSchedule.Daily, habit.schedule)
        assertTrue(habit.isActiveOn(start.plusDays(3)))
    }

    @Test
    fun `pauses are mapped and an open pause is still paused`() {
        val habit = HabitWithDetails(
            entity(),
            schedule = null,
            reminders = emptyList(),
            pauses = listOf(
                HabitPauseEntity(habitId = 7, fromEpochDay = start.toEpochDay(), toEpochDay = start.plusDays(2).toEpochDay()),
                HabitPauseEntity(habitId = 7, fromEpochDay = start.plusDays(10).toEpochDay(), toEpochDay = null),
            ),
        ).toDomain()

        assertTrue(habit.isPaused)
        assertTrue(habit.isPausedOn(start.plusDays(1)))
        assertTrue(!habit.isPausedOn(start.plusDays(5)))
        assertTrue(habit.isPausedOn(start.plusDays(50)))
        assertEquals(start.plusDays(2), habit.pauses.first().to)
        assertNull(habit.pauses.last().to)
    }

    @Test
    fun `blank reminder messages are stored as null`() {
        val reminder = Reminder(time = LocalTime.of(9, 0), message = "   ")
        assertNull(reminder.toEntity(1).message)
    }
}
