package com.example.trackly.domain

import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.logic.StreakInfo
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.PauseRange
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.fakes.Fixtures
import com.example.trackly.fakes.Fixtures.TODAY
import com.example.trackly.fakes.Fixtures.doneOn
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakCalculatorTest {

    private fun d(offset: Long): LocalDate = TODAY.minusDays(offset)

    @Test
    fun `no entries means no streak`() {
        val habit = Fixtures.habit()
        assertEquals(StreakInfo(0, 0), StreakCalculator.compute(habit, emptyList(), TODAY))
    }

    @Test
    fun `daily habit counts consecutive days including today`() {
        val habit = Fixtures.habit()
        val entries = doneOn(habit.id, d(3), d(2), d(1), d(0))
        assertEquals(StreakInfo(4, 4), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `today not yet done does not break the streak`() {
        val habit = Fixtures.habit()
        val entries = doneOn(habit.id, d(3), d(2), d(1))
        assertEquals(StreakInfo(3, 3), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `missing a day resets the current streak but keeps the best`() {
        val habit = Fixtures.habit()
        val entries = doneOn(habit.id, d(6), d(5), d(4), d(3), d(1), d(0))
        assertEquals(StreakInfo(2, 4), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `a missed yesterday means the current streak is zero even if today is pending`() {
        val habit = Fixtures.habit()
        val entries = doneOn(habit.id, d(5), d(4), d(3), d(2))
        assertEquals(StreakInfo(0, 4), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `unscheduled days do not break the streak of a Mon Wed Fri habit`() {
        val schedule = HabitSchedule(ScheduleType.SPECIFIC_DAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val habit = Fixtures.habit(schedule = schedule)
        // TODAY is Wednesday 2026-10-14.
        val entries = doneOn(
            habit.id,
            LocalDate.of(2026, 10, 5), // Mon
            LocalDate.of(2026, 10, 7), // Wed
            LocalDate.of(2026, 10, 9), // Fri
            LocalDate.of(2026, 10, 12), // Mon
            LocalDate.of(2026, 10, 14), // Wed (today)
        )
        assertEquals(StreakInfo(5, 5), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `a missed scheduled day breaks the streak of a Mon Wed Fri habit`() {
        val schedule = HabitSchedule(ScheduleType.SPECIFIC_DAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val habit = Fixtures.habit(schedule = schedule)
        val entries = doneOn(
            habit.id,
            LocalDate.of(2026, 10, 7), // Wed
            // Fri 10-09 missed
            LocalDate.of(2026, 10, 12), // Mon
            LocalDate.of(2026, 10, 14), // Wed (today)
        )
        assertEquals(StreakInfo(2, 2), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `pending today on a scheduled-days habit keeps the previous scheduled days`() {
        val schedule = HabitSchedule(ScheduleType.SPECIFIC_DAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val habit = Fixtures.habit(schedule = schedule)
        val entries = doneOn(habit.id, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 12))
        assertEquals(StreakInfo(3, 3), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `paused days neither extend nor break the streak`() {
        val habit = Fixtures.habit(pauses = listOf(PauseRange(d(3), d(2))))
        val entries = doneOn(habit.id, d(5), d(4), d(1), d(0))
        assertEquals(StreakInfo(4, 4), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `entries before the start date are ignored`() {
        val habit = Fixtures.habit(start = d(2))
        val entries = doneOn(habit.id, d(5), d(4), d(3), d(2), d(1), d(0))
        assertEquals(StreakInfo(3, 3), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `every other day schedule counts only its own days`() {
        val habit = Fixtures.habit(
            schedule = HabitSchedule(ScheduleType.EVERY_N_DAYS, intervalDays = 2),
            start = d(6),
        )
        val entries = doneOn(habit.id, d(6), d(4), d(2), d(0))
        assertEquals(StreakInfo(4, 4), StreakCalculator.compute(habit, entries, TODAY))
    }

    @Test
    fun `incomplete partial progress does not count as a completion`() {
        val habit = Fixtures.habit()
        val entries = listOf(Fixtures.entry(habit.id, d(1), 7.0), Fixtures.done(habit.id, d(0)))
        assertEquals(StreakInfo(1, 1), StreakCalculator.compute(habit, entries, TODAY))
    }
}
