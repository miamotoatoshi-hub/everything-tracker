package com.example.trackly.domain

import com.example.trackly.domain.logic.AchievementKind
import com.example.trackly.domain.logic.Achievements
import com.example.trackly.domain.logic.RestDay
import com.example.trackly.domain.model.HabitSchedule
import com.example.trackly.domain.model.PauseRange
import com.example.trackly.domain.model.ScheduleType
import com.example.trackly.fakes.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private val today = Fixtures.TODAY

    @Test
    fun `nothing is unlocked without history`() {
        val stats = Achievements.stats(listOf(Fixtures.habit(id = 1)), emptyList(), today)
        val progress = Achievements.progress(stats)
        assertTrue(progress.none { it.unlocked })
        assertEquals(Achievements.all.size, progress.size)
    }

    @Test
    fun `stats count completions, active days, best streak and habits`() {
        val water = Fixtures.habit(id = 1)
        val read = Fixtures.habit(id = 2, name = "Read")
        val entries = Fixtures.doneOn(1, today, today.minusDays(1), today.minusDays(2)) +
            Fixtures.doneOn(2, today) +
            Fixtures.entry(2, today.minusDays(1), 1.0) // logged, but the goal was not reached
        val stats = Achievements.stats(listOf(water, read), entries, today)

        assertEquals(4, stats.completions)
        assertEquals(3, stats.activeDays)
        assertEquals(3, stats.bestStreak)
        assertEquals(2, stats.habits)
        assertEquals(1, stats.perfectDays) // only today has both habits done

        val progress = Achievements.progress(stats)
        assertTrue(progress.single { it.achievement.id == "first_step" }.unlocked)
        assertTrue(progress.single { it.achievement.id == "streak_3" }.unlocked)
        assertFalse(progress.single { it.achievement.id == "streak_7" }.unlocked)
        assertTrue(progress.single { it.achievement.id == "perfect_1" }.unlocked)
    }

    @Test
    fun `a day only counts as perfect when every due habit is done`() {
        val daily = Fixtures.habit(id = 1)
        val weekends = Fixtures.habit(id = 2, schedule = HabitSchedule(ScheduleType.WEEKENDS))
        // Fixtures.TODAY is a Wednesday: the weekend habit is not due, the daily one alone makes it perfect.
        assertEquals(1, Achievements.perfectDays(listOf(daily, weekends), Fixtures.doneOn(1, today), today))
        // A day that is in the future does not count.
        assertEquals(0, Achievements.perfectDays(listOf(daily), Fixtures.doneOn(1, today.plusDays(1)), today))
    }

    @Test
    fun `next is the locked achievement closest to being earned`() {
        val stats = Achievements.stats(listOf(Fixtures.habit(id = 1)), Fixtures.doneOn(1, today, today.minusDays(1)), today)
        val next = Achievements.next(Achievements.progress(stats))
        assertEquals(AchievementKind.STREAK, next?.achievement?.kind)
        assertEquals("streak_3", next?.achievement?.id)
    }

    @Test
    fun `next is null when everything is unlocked`() {
        val maxed = Achievements.all.map { com.example.trackly.domain.logic.AchievementProgress(it, it.target) }
        assertNull(Achievements.next(maxed))
    }

    @Test
    fun `a rest day is a one-day pause on a due, unfinished day`() {
        val habit = Fixtures.habit(id = 1)
        assertTrue(RestDay.canTake(habit, today, completed = false))
        assertFalse(RestDay.canTake(habit, today, completed = true))

        val resting = habit.copy(pauses = listOf(PauseRange(today, today)))
        assertTrue(RestDay.isRestDay(resting, today))
        assertFalse(resting.isActiveOn(today))
        assertTrue(resting.isActiveOn(today.plusDays(1)))

        val paused = habit.copy(pauses = listOf(PauseRange(today.minusDays(3), null)))
        assertFalse(RestDay.isRestDay(paused, today))
        assertFalse(RestDay.canTake(paused, today, completed = false))
    }
}
