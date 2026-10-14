package com.example.trackly.domain

import com.example.trackly.domain.logic.GoalProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalProgressTest {
    @Test
    fun `completion is reached exactly at the goal`() {
        assertTrue(GoalProgress.isComplete(8.0, 8.0))
        assertTrue(GoalProgress.isComplete(9.0, 8.0))
        assertFalse(GoalProgress.isComplete(7.0, 8.0))
        assertFalse(GoalProgress.isComplete(0.0, 8.0))
    }

    @Test
    fun `a zero goal can never be completed`() {
        assertFalse(GoalProgress.isComplete(5.0, 0.0))
        assertEquals(0f, GoalProgress.fraction(5.0, 0.0), 0f)
    }

    @Test
    fun `fraction is clamped to 0 until 1`() {
        assertEquals(0.625f, GoalProgress.fraction(5.0, 8.0), 0.0001f)
        assertEquals(1f, GoalProgress.fraction(20.0, 8.0), 0f)
        assertEquals(0f, GoalProgress.fraction(-3.0, 8.0), 0f)
    }

    @Test
    fun `percent rounds`() {
        assertEquals(63, GoalProgress.percent(5.0, 8.0))
        assertEquals(100, GoalProgress.percent(8.0, 8.0))
    }

    @Test
    fun `applyDelta never goes below zero and ignores float noise`() {
        assertEquals(0.0, GoalProgress.applyDelta(2.0, -5.0), 0.0)
        assertEquals(0.3, GoalProgress.applyDelta(0.1, 0.2), 0.0)
        assertEquals(6.0, GoalProgress.applyDelta(5.0, 1.0), 0.0)
        assertEquals(0.0, GoalProgress.applyDelta(1.0, -1.0), 0.0)
    }

    @Test
    fun `fractional goals work`() {
        assertTrue(GoalProgress.isComplete(2.5, 2.5))
        assertEquals(0.5f, GoalProgress.fraction(1.25, 2.5), 0.0001f)
    }
}
