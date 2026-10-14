package com.example.trackly.domain

import com.example.trackly.domain.logic.DateRange
import com.example.trackly.domain.logic.DayState
import com.example.trackly.domain.logic.DayStateResolver
import com.example.trackly.domain.logic.StatsCalculator
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.fakes.Fixtures
import com.example.trackly.fakes.Fixtures.TODAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsCalculatorTest {
    private fun d(offset: Long): LocalDate = TODAY.minusDays(offset)

    @Test
    fun `day state resolution`() {
        val habit = Fixtures.habit()
        assertEquals(DayState.COMPLETED, DayStateResolver.resolve(habit, Fixtures.done(1, d(2)), d(2), TODAY))
        assertEquals(DayState.PARTIAL, DayStateResolver.resolve(habit, Fixtures.entry(1, d(2), 3.0), d(2), TODAY))
        assertEquals(DayState.MISSED, DayStateResolver.resolve(habit, null, d(2), TODAY))
        assertEquals(DayState.PENDING, DayStateResolver.resolve(habit, null, TODAY, TODAY))
        assertEquals(DayState.FUTURE, DayStateResolver.resolve(habit, null, TODAY.plusDays(1), TODAY))
        assertEquals(DayState.NOT_SCHEDULED, DayStateResolver.resolve(habit, null, habit.startDate.minusDays(1), TODAY))
    }

    @Test
    fun `unscheduled days are not missed`() {
        val habit = Fixtures.habit(schedule = HabitSchedule(ScheduleType.WEEKENDS))
        // d(1) is Tuesday.
        assertEquals(DayState.NOT_SCHEDULED, DayStateResolver.resolve(habit, null, d(1), TODAY))
    }

    @Test
    fun `aggregate day state`() {
        val day = d(3)
        assertEquals(DayState.COMPLETED, DayStateResolver.resolveAggregate(3, 3, 0, day, TODAY))
        assertEquals(DayState.PARTIAL, DayStateResolver.resolveAggregate(3, 1, 0, day, TODAY))
        assertEquals(DayState.PARTIAL, DayStateResolver.resolveAggregate(3, 0, 1, day, TODAY))
        assertEquals(DayState.MISSED, DayStateResolver.resolveAggregate(3, 0, 0, day, TODAY))
        assertEquals(DayState.NOT_SCHEDULED, DayStateResolver.resolveAggregate(0, 0, 0, day, TODAY))
        assertEquals(DayState.PENDING, DayStateResolver.resolveAggregate(2, 0, 0, TODAY, TODAY))
        assertEquals(DayState.FUTURE, DayStateResolver.resolveAggregate(2, 0, 0, TODAY.plusDays(2), TODAY))
    }

    @Test
    fun `completion rate excludes a pending today`() {
        val habit = Fixtures.habit()
        val entries = listOf(
            Fixtures.done(1, d(6)), Fixtures.done(1, d(5)), Fixtures.done(1, d(4)), Fixtures.done(1, d(3)),
            Fixtures.entry(1, d(1), 4.0), // partial; d(2) missed; today pending
        )
        val stats = StatsCalculator.habitStats(habit, entries, DateRange.lastDays(7, TODAY), TODAY)
        assertEquals(6, stats.eligibleDays)
        assertEquals(4, stats.completedDays)
        assertEquals(1, stats.partialDays)
        assertEquals(1, stats.missedDays)
        assertEquals(4f / 6f, stats.completionRate, 0.0001f)
        assertEquals(0.75f, stats.averageProgress, 0.0001f)
        assertEquals(36.0, stats.totalValue, 0.0)
        assertEquals(7, stats.cells.size)
    }

    @Test
    fun `a completed today counts toward the rate`() {
        val habit = Fixtures.habit()
        val entries = listOf(Fixtures.done(1, TODAY), Fixtures.done(1, d(1)))
        val stats = StatsCalculator.habitStats(habit, entries, DateRange.lastDays(2, TODAY), TODAY)
        assertEquals(2, stats.eligibleDays)
        assertEquals(1f, stats.completionRate, 0f)
    }

    @Test
    fun `rate is zero when nothing is eligible yet`() {
        val habit = Fixtures.habit(start = TODAY)
        val stats = StatsCalculator.habitStats(habit, emptyList(), DateRange.lastDays(7, TODAY), TODAY)
        assertEquals(0, stats.eligibleDays)
        assertEquals(0f, stats.completionRate, 0f)
    }

    @Test
    fun `scheduled days only are counted for a weekdays habit`() {
        val habit = Fixtures.habit(schedule = HabitSchedule(ScheduleType.WEEKDAYS))
        // Week Mon 10-12 .. Sun 10-18; today is Wednesday so eligible = Mon, Tue.
        val stats = StatsCalculator.habitStats(
            habit, listOf(Fixtures.done(1, LocalDate.of(2026, 10, 12))), DateRange.week(TODAY, DayOfWeek.MONDAY), TODAY,
        )
        assertEquals(2, stats.eligibleDays)
        assertEquals(1, stats.completedDays)
    }

    @Test
    fun `overall stats aggregate every habit`() {
        val a = Fixtures.habit(id = 1)
        val b = Fixtures.habit(id = 2)
        val entries = listOf(
            Fixtures.done(1, d(1)),
            Fixtures.done(1, TODAY), Fixtures.done(2, TODAY),
        )
        val stats = StatsCalculator.overall(listOf(a, b), entries, DateRange.lastDays(2, TODAY), TODAY)
        assertEquals(4, stats.eligibleSlots)
        assertEquals(3, stats.completedSlots)
        assertEquals(0.75f, stats.completionRate, 0.0001f)
        assertEquals(1, stats.perfectDays)
        assertEquals(2, stats.days.size)
        assertEquals(1f, stats.days.last().fraction, 0f)
        assertEquals(0.5f, stats.days.first().fraction, 0f)
    }

    @Test
    fun `weekly summary`() {
        val habit = Fixtures.habit(id = 1)
        val entries = listOf(
            Fixtures.done(1, LocalDate.of(2026, 10, 12)),
            Fixtures.done(1, LocalDate.of(2026, 10, 13)),
            Fixtures.done(1, TODAY),
        )
        val summary = StatsCalculator.weeklySummary(listOf(habit), entries, TODAY, DayOfWeek.MONDAY)
        assertEquals(1, summary.habitCount)
        assertEquals(3, summary.completions)
        assertEquals(1f, summary.completionRate, 0f)
        assertEquals(3, summary.bestStreak)
        assertEquals(7, summary.days.size)
    }

    @Test
    fun `insights find the best weekday and the most consistent habit`() {
        val habit = Fixtures.habit(id = 1)
        // Complete every Monday in the last 8 weeks and nothing else.
        val mondays = (0L..55L).map { TODAY.minusDays(it) }.filter { it.dayOfWeek == DayOfWeek.MONDAY }
        val entries = mondays.map { Fixtures.done(1, it) }
        val insights = StatsCalculator.insights(listOf(habit), entries, TODAY)
        assertEquals(DayOfWeek.MONDAY, insights.bestDay)
        assertNotNull(insights.weakestDay)
        assertTrue(insights.weakestDay != DayOfWeek.MONDAY)
        assertNotNull(insights.topHabit)
    }

    @Test
    fun `insights without data are empty`() {
        val insights = StatsCalculator.insights(emptyList(), emptyList(), TODAY)
        assertNull(insights.bestDay)
        assertNull(insights.topHabit)
        assertNull(insights.weeklyTrend)
        assertEquals(0, insights.perfectDaysLast30)
    }
}
