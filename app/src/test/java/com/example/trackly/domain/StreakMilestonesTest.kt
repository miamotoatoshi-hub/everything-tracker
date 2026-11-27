package com.example.trackly.domain

import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.logic.StreakMilestones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StreakMilestonesTest {

    @Test
    fun `reaching a milestone day is celebrated`() {
        assertEquals(3, StreakMilestones.reached(2, 3))
        assertEquals(7, StreakMilestones.reached(6, 7))
        assertEquals(30, StreakMilestones.reached(29, 30))
        assertEquals(365, StreakMilestones.reached(364, 365))
    }

    @Test
    fun `days between milestones are not`() {
        assertNull(StreakMilestones.reached(0, 1))
        assertNull(StreakMilestones.reached(7, 8))
        assertNull(StreakMilestones.reached(30, 31))
    }

    @Test
    fun `a streak that does not grow is not celebrated`() {
        assertNull(StreakMilestones.reached(7, 7))
        assertNull(StreakMilestones.reached(8, 7))
        assertNull(StreakMilestones.reached(0, 0))
    }

    @Test
    fun `a jump over several milestones celebrates the largest`() {
        assertEquals(14, StreakMilestones.reached(5, 20))
    }

    @Test
    fun `completing today adds one day only when today is scheduled`() {
        assertEquals(7, StreakMilestones.afterCompletingToday(6, scheduledToday = true))
        assertEquals(6, StreakMilestones.afterCompletingToday(6, scheduledToday = false))
    }

    @Test
    fun `the shortcut agrees with the streak engine`() {
        val today = LocalDate.of(2026, 10, 14)
        val from = today.minusDays(20)
        val done = (1L..6L).map { today.minusDays(it) }.toMutableSet()
        val before = StreakCalculator.compute(from, today, isScheduled = { true }, isDone = { it in done }).current
        done += today
        val after = StreakCalculator.compute(from, today, isScheduled = { true }, isDone = { it in done }).current

        assertEquals(6, before)
        assertEquals(after, StreakMilestones.afterCompletingToday(before, scheduledToday = true))
        assertEquals(7, StreakMilestones.reached(before, after))
    }
}
