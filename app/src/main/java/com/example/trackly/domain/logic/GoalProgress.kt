package com.example.trackly.domain.logic

/** Pure helpers for turning a logged value into progress toward the goal. */
object GoalProgress {
    private const val EPSILON = 1e-9

    fun isComplete(value: Double, goal: Double): Boolean = goal > 0.0 && value + EPSILON >= goal

    /** Progress in 0..1 (clamped); a non-positive goal yields 0. */
    fun fraction(value: Double, goal: Double): Float {
        if (goal <= 0.0 || value <= 0.0) return 0f
        return (value / goal).coerceIn(0.0, 1.0).toFloat()
    }

    fun percent(value: Double, goal: Double): Int = Math.round(fraction(value, goal) * 100f)

    /** Applies [delta] and never goes below zero. Rounds away floating point noise. */
    fun applyDelta(current: Double, delta: Double): Double {
        val result = current + delta
        if (result <= EPSILON) return 0.0
        return Math.round(result * 1000.0) / 1000.0
    }
}
