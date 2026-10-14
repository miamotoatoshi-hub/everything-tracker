package com.example.trackly.domain

import com.example.trackly.domain.logic.DateRange
import com.example.trackly.domain.logic.DateUtils
import com.example.trackly.domain.logic.StatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DateRangeTest {
    private val wednesday = LocalDate.of(2026, 10, 14)

    @Test
    fun `week starting on monday`() {
        val r = DateRange.week(wednesday, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 12), r.start)
        assertEquals(LocalDate.of(2026, 10, 18), r.end)
        assertEquals(7, r.length)
    }

    @Test
    fun `week starting on sunday`() {
        val r = DateRange.week(wednesday, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 11), r.start)
        assertEquals(LocalDate.of(2026, 10, 17), r.end)
    }

    @Test
    fun `a sunday belongs to the week that started the previous monday`() {
        val r = DateRange.week(LocalDate.of(2026, 10, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 5), r.start)
    }

    @Test
    fun `the first day of the week is its own start`() {
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(monday, DateRange.week(monday, DayOfWeek.MONDAY).start)
    }

    @Test
    fun `month and leap year lengths`() {
        assertEquals(31, DateRange.month(YearMonth.of(2026, 10)).length)
        assertEquals(28, DateRange.month(YearMonth.of(2026, 2)).length)
        assertEquals(29, DateRange.month(YearMonth.of(2028, 2)).length)
        assertEquals(366, DateRange.year(2028).length)
        assertEquals(365, DateRange.year(2026).length)
    }

    @Test
    fun `days lists every date inclusively`() {
        val r = DateRange(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2))
        assertEquals(4, r.days().size)
        assertEquals(LocalDate.of(2027, 1, 1), r.days()[2])
    }

    @Test
    fun `contains is inclusive`() {
        val r = DateRange.week(wednesday, DayOfWeek.MONDAY)
        assertTrue(LocalDate.of(2026, 10, 12) in r)
        assertTrue(LocalDate.of(2026, 10, 18) in r)
        assertFalse(LocalDate.of(2026, 10, 19) in r)
    }

    @Test
    fun `previous range has the same length and ends the day before`() {
        val r = DateRange.lastDays(7, wednesday)
        val p = r.previous()
        assertEquals(7, p.length)
        assertEquals(r.start.minusDays(1), p.end)
    }

    @Test
    fun `end before start is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(wednesday, wednesday.minusDays(1))
        }
    }

    @Test
    fun `week days are ordered from the first day`() {
        val sunday = DateUtils.orderedWeekDays(DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, sunday.first())
        assertEquals(DayOfWeek.SATURDAY, sunday.last())
        assertEquals(DayOfWeek.MONDAY, DateUtils.orderedWeekDays(DayOfWeek.MONDAY).first())
    }

    @Test
    fun `leading blanks in a month grid`() {
        // 2026-10-01 is a Thursday.
        assertEquals(3, DateUtils.leadingBlanks(YearMonth.of(2026, 10), DayOfWeek.MONDAY))
        assertEquals(4, DateUtils.leadingBlanks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY))
        assertEquals(0, DateUtils.leadingBlanks(YearMonth.of(2026, 10), DayOfWeek.THURSDAY))
    }

    @Test
    fun `range for period`() {
        assertEquals(7, DateUtils.rangeFor(StatsPeriod.WEEK, wednesday, DayOfWeek.MONDAY).length)
        assertEquals(31, DateUtils.rangeFor(StatsPeriod.MONTH, wednesday, DayOfWeek.MONDAY).length)
        assertEquals(365, DateUtils.rangeFor(StatsPeriod.YEAR, wednesday, DayOfWeek.MONDAY).length)
    }
}
