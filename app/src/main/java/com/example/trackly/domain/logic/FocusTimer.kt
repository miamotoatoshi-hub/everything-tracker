package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitUnit
import kotlin.math.ceil

/** Pure helpers for the Premium focus timer of duration habits. */
object FocusTimer {
    val presetMinutes: List<Int> = listOf(5, 10, 15, 25, 45, 60)
    const val DEFAULT_MINUTES = 25
    const val MAX_MINUTES = 180

    /** The value to add to the day for [minutes] of focus, in the habit's own unit. */
    fun loggedValue(minutes: Int, unit: HabitUnit): Double =
        if (unit == HabitUnit.HOURS) minutes / 60.0 else minutes.toDouble()

    /** What is left of today's goal, in minutes (0 when already reached). */
    fun minutesLeft(habit: Habit, todayValue: Double): Int {
        val left = (habit.goalValue - todayValue).coerceAtLeast(0.0)
        val minutes = if (habit.unit == HabitUnit.HOURS) left * 60 else left
        return ceil(minutes).toInt()
    }

    /** Suggested session: the rest of today's goal rounded up to 5 minutes, or 25 when the goal is done. */
    fun suggestedMinutes(habit: Habit, todayValue: Double): Int {
        val left = minutesLeft(habit, todayValue)
        if (left <= 0) return DEFAULT_MINUTES
        return (((left + 4) / 5) * 5).coerceIn(5, MAX_MINUTES)
    }

    /** "mm:ss", or "h:mm:ss" for an hour and more. */
    fun format(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
