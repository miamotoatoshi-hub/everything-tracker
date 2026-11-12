package com.example.trackly.widget

import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.PauseRange
import com.example.trackly.fakes.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWidgetDataTest {
    private val today = Fixtures.TODAY

    @Test
    fun `shows today's active habits with pending ones first`() {
        val habits = listOf(
            Fixtures.habit(id = 1, name = "Water"),
            Fixtures.habit(id = 2, name = "Read", type = HabitType.BOOLEAN, goal = 1.0),
            Fixtures.habit(id = 3, name = "Old", archived = true),
            Fixtures.habit(id = 4, name = "Paused", pauses = listOf(PauseRange(today.minusDays(1), null))),
            Fixtures.habit(id = 5, name = "Later", start = today.plusDays(3)),
        )
        val entries = listOf(
            Fixtures.done(1, today),
            Fixtures.done(1, today.minusDays(1)),
            Fixtures.entry(2, today.minusDays(1), 1.0, goal = 1.0),
        )

        val state = TodayWidgetData.build(habits, entries, today, isPremium = true)

        assertEquals(listOf("Read", "Water"), state.habits.map { it.name })
        assertFalse(state.habits[0].completed)
        assertTrue(state.habits[0].isBoolean)
        assertTrue(state.habits[1].completed)
        assertEquals(2, state.habits[1].streak)
        assertEquals(1, state.completed)
        assertEquals(50, state.percent)
    }

    @Test
    fun `free users get the locked state`() {
        val state = TodayWidgetData.build(listOf(Fixtures.habit()), emptyList(), today, isPremium = false)
        assertFalse(state.isPremium)
        assertEquals(0, state.percent)
    }
}
