package com.example.trackly.domain

import com.example.trackly.core.security.AppLock
import com.example.trackly.core.security.PinHasher
import com.example.trackly.domain.logic.FocusTimer
import com.example.trackly.domain.logic.Reports
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import com.example.trackly.fakes.Fixtures
import com.example.trackly.notifications.DailySummaryScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.YearMonth

class PremiumPackTest {
    private val today = Fixtures.TODAY // Wednesday, 14 October 2026

    // region PIN

    @Test
    fun `a PIN is stored only as a salted hash and verifies`() {
        val stored = PinHasher.hash("1234")
        assertFalse(stored.contains("1234"))
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("4321", stored))
        assertFalse(PinHasher.verify("1234", null))
        assertNotEquals(stored, PinHasher.hash("1234")) // a new salt every time
    }

    @Test
    fun `only four digits make a PIN`() {
        assertTrue(PinHasher.isValidPin("0907"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    // endregion

    // region app lock

    @Test
    fun `the lock comes back only after a real absence`() {
        var clock = 0L
        val lock = AppLock(now = { clock })
        assertTrue(lock.locked.value)
        lock.unlock()

        lock.onBackground()
        clock += 5_000
        lock.onForeground(lockEnabled = true)
        assertFalse(lock.locked.value) // a quick trip to the share sheet

        lock.onBackground()
        clock += AppLock.GRACE_MILLIS
        lock.onForeground(lockEnabled = true)
        assertTrue(lock.locked.value)

        lock.unlock()
        lock.onBackground()
        clock += AppLock.GRACE_MILLIS * 2
        lock.onForeground(lockEnabled = false)
        assertFalse(lock.locked.value) // no PIN set
    }

    // endregion

    // region focus timer

    @Test
    fun `focus minutes are logged in the habit's unit`() {
        assertEquals(25.0, FocusTimer.loggedValue(25, HabitUnit.MINUTES), 0.0)
        assertEquals(0.5, FocusTimer.loggedValue(30, HabitUnit.HOURS), 1e-9)
    }

    @Test
    fun `the suggested session covers the rest of the goal`() {
        val meditation = Fixtures.habit(id = 1, type = HabitType.DURATION, goal = 20.0, unit = HabitUnit.MINUTES)
        assertEquals(20, FocusTimer.suggestedMinutes(meditation, 0.0))
        assertEquals(10, FocusTimer.suggestedMinutes(meditation, 12.0)) // 8 left, rounded up to 5s
        assertEquals(FocusTimer.DEFAULT_MINUTES, FocusTimer.suggestedMinutes(meditation, 20.0))
        val study = Fixtures.habit(id = 2, type = HabitType.DURATION, goal = 1.5, unit = HabitUnit.HOURS)
        assertEquals(90, FocusTimer.suggestedMinutes(study, 0.0))
    }

    @Test
    fun `the timer reads like a clock`() {
        assertEquals("25:00", FocusTimer.format(1500))
        assertEquals("00:07", FocusTimer.format(7))
        assertEquals("1:05:00", FocusTimer.format(3900))
        assertEquals("00:00", FocusTimer.format(-3))
    }

    // endregion

    // region reports

    @Test
    fun `a month report counts due and completed goal-days`() {
        val water = Fixtures.habit(id = 1, start = today.minusDays(60))
        val read = Fixtures.habit(id = 2, name = "Read", start = today.minusDays(60))
        // October 1..14: water done every day, read on 7 days.
        val entries = (0L..13L).flatMap { Fixtures.doneOn(1, today.minusDays(it)) } +
            (0L..6L).flatMap { Fixtures.doneOn(2, today.minusDays(it)) }

        val report = Reports.month(listOf(water, read), entries, YearMonth.from(today), today)

        assertEquals(28, report.due)
        assertEquals(21, report.done)
        assertEquals(0.75f, report.rate, 1e-6f)
        assertEquals(7, report.perfectDays)
        assertEquals(14, report.activeDays)
        assertEquals(14, report.bestStreak)
        assertEquals(1L, report.bestStreakHabit?.id)
        assertEquals(listOf(1L, 2L), report.habits.map { it.habit.id })
        assertEquals(0f, report.previousRate ?: -1f, 0f) // September: due, nothing done
    }

    @Test
    fun `the best weekday has the highest completion rate`() {
        val habit = Fixtures.habit(id = 1, start = today.minusDays(60))
        // Only Wednesdays are done in October (7th and 14th).
        val entries = Fixtures.doneOn(1, today, today.minusDays(7))
        val report = Reports.month(listOf(habit), entries, YearMonth.from(today), today)
        assertEquals(DayOfWeek.WEDNESDAY, report.bestWeekday)
        assertEquals(1f, report.weekdayRates[DayOfWeek.WEDNESDAY] ?: 0f, 0f)
    }

    @Test
    fun `a month before any habit is empty`() {
        val habit = Fixtures.habit(id = 1, start = today.minusDays(3))
        val report = Reports.month(listOf(habit), emptyList(), YearMonth.from(today).minusMonths(2), today)
        assertTrue(report.isEmpty)
        assertNull(report.previousRate)
    }

    @Test
    fun `year pixels hold one rate per due day up to today`() {
        val habit = Fixtures.habit(id = 1, start = today.minusDays(2))
        val read = Fixtures.habit(id = 2, name = "Read", start = today.minusDays(2))
        val pixels = Reports.yearPixels(listOf(habit, read), Fixtures.doneOn(1, today) + Fixtures.doneOn(2, today), today.year, today)
        assertEquals(3, pixels.size)
        assertEquals(1f, pixels[today] ?: 0f, 0f)
        assertEquals(0f, pixels[today.minusDays(1)] ?: -1f, 0f)
        assertFalse(pixels.containsKey(today.plusDays(1)))
    }

    // endregion

    // region evening summary

    @Test
    fun `the evening summary fires at the next occurrence of its time`() {
        val evening = 21 * 60
        assertEquals(60 * 60_000L, DailySummaryScheduler.delayMillis(LocalDateTime.of(2026, 10, 14, 20, 0), evening))
        assertEquals(23 * 60 * 60_000L, DailySummaryScheduler.delayMillis(LocalDateTime.of(2026, 10, 14, 22, 0), evening))
        assertEquals(24 * 60 * 60_000L, DailySummaryScheduler.delayMillis(LocalDateTime.of(2026, 10, 14, 21, 0), evening))
    }

    @Test
    fun `the summary lists a few habits and counts the rest`() {
        val habits = (1L..6L).map { Fixtures.habit(id = it, name = "H$it") }
        assertEquals("💧 H1, 💧 H2, 💧 H3, 💧 H4 +2", DailySummaryScheduler.habitList(habits))
    }

    // endregion
}
