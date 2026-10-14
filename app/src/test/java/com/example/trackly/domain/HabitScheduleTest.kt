package com.example.trackly.domain

import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitScheduleTest {
    private val start = LocalDate.of(2026, 10, 1) // Thursday
    private val wednesday = LocalDate.of(2026, 10, 14)
    private val saturday = LocalDate.of(2026, 10, 17)

    @Test
    fun `every day is scheduled on any day after the start`() {
        val s = HabitSchedule.Daily
        assertTrue(s.isScheduledOn(wednesday, start))
        assertTrue(s.isScheduledOn(saturday, start))
        assertTrue(s.isScheduledOn(start, start))
    }

    @Test
    fun `nothing is scheduled before the start date`() {
        assertFalse(HabitSchedule.Daily.isScheduledOn(start.minusDays(1), start))
    }

    @Test
    fun `weekdays and weekends`() {
        val weekdays = HabitSchedule(ScheduleType.WEEKDAYS)
        val weekends = HabitSchedule(ScheduleType.WEEKENDS)
        assertTrue(weekdays.isScheduledOn(wednesday, start))
        assertFalse(weekdays.isScheduledOn(saturday, start))
        assertFalse(weekends.isScheduledOn(wednesday, start))
        assertTrue(weekends.isScheduledOn(saturday, start))
    }

    @Test
    fun `specific days`() {
        val s = HabitSchedule(ScheduleType.SPECIFIC_DAYS, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertTrue(s.isScheduledOn(wednesday, start))
        assertTrue(s.isScheduledOn(LocalDate.of(2026, 10, 16), start))
        assertFalse(s.isScheduledOn(saturday, start))
    }

    @Test
    fun `specific days with no days selected is never scheduled`() {
        val s = HabitSchedule(ScheduleType.SPECIFIC_DAYS, emptySet())
        assertFalse(s.isScheduledOn(wednesday, start))
    }

    @Test
    fun `every third day is anchored at the start date`() {
        val s = HabitSchedule(ScheduleType.EVERY_N_DAYS, intervalDays = 3)
        val scheduled = (0L..14L).map { start.plusDays(it) }.filter { s.isScheduledOn(it, start) }
        assertEquals(
            listOf(1, 4, 7, 10, 13).map { LocalDate.of(2026, 10, it) },
            scheduled,
        )
    }

    @Test
    fun `day masks round trip`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.THURSDAY)
        val mask = HabitSchedule.daysToMask(days)
        assertEquals(1 or 8 or 64, mask)
        assertEquals(days, HabitSchedule.maskToDays(mask))
        assertEquals(emptySet<DayOfWeek>(), HabitSchedule.maskToDays(0))
        assertEquals(HabitSchedule.ALL_DAYS, HabitSchedule.maskToDays(127))
    }
}
