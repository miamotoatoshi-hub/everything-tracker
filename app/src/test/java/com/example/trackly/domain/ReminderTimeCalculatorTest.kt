package com.example.trackly.domain

import com.example.trackly.domain.logic.ReminderTimeCalculator
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.PauseRange
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.ReminderRepeat
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.fakes.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderTimeCalculatorTest {
    private val eight = LocalTime.of(8, 0)
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private val weekdays = HabitSchedule(ScheduleType.WEEKDAYS)

    @Test
    fun `fires later today when the time has not passed`() {
        val habit = Fixtures.habit()
        val reminder = Reminder(time = eight)
        assertEquals(at(14, 8), ReminderTimeCalculator.nextTrigger(habit, reminder, at(14, 7, 59)))
    }

    @Test
    fun `fires tomorrow when the time has passed`() {
        val habit = Fixtures.habit()
        val reminder = Reminder(time = eight)
        assertEquals(at(15, 8), ReminderTimeCalculator.nextTrigger(habit, reminder, at(14, 8)))
    }

    @Test
    fun `follows the habit schedule by default`() {
        val habit = Fixtures.habit(schedule = weekdays)
        val reminder = Reminder(time = eight)
        // Friday 2026-10-09 09:00 -> next weekday is Monday the 12th.
        assertEquals(at(12, 8), ReminderTimeCalculator.nextTrigger(habit, reminder, at(9, 9)))
    }

    @Test
    fun `every day repeat ignores the habit schedule`() {
        val habit = Fixtures.habit(schedule = weekdays)
        val reminder = Reminder(time = eight, repeat = ReminderRepeat.EVERY_DAY)
        assertEquals(at(10, 8), ReminderTimeCalculator.nextTrigger(habit, reminder, at(9, 9)))
    }

    @Test
    fun `disabled and archived reminders never fire`() {
        assertNull(ReminderTimeCalculator.nextTrigger(Fixtures.habit(), Reminder(time = eight, enabled = false), at(14, 7)))
        assertNull(ReminderTimeCalculator.nextTrigger(Fixtures.habit(archived = true), Reminder(time = eight), at(14, 7)))
    }

    @Test
    fun `skips paused days`() {
        val habit = Fixtures.habit(pauses = listOf(PauseRange(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16))))
        assertEquals(at(17, 8), ReminderTimeCalculator.nextTrigger(habit, Reminder(time = eight), at(14, 9)))
    }

    @Test
    fun `an indefinitely paused habit has no next reminder`() {
        val habit = Fixtures.habit(pauses = listOf(PauseRange(LocalDate.of(2026, 10, 1), null)))
        assertNull(ReminderTimeCalculator.nextTrigger(habit, Reminder(time = eight), at(14, 9)))
    }

    @Test
    fun `waits for the start date`() {
        val habit = Fixtures.habit(start = LocalDate.of(2026, 10, 20))
        assertEquals(at(20, 8), ReminderTimeCalculator.nextTrigger(habit, Reminder(time = eight), at(14, 9)))
    }
}
