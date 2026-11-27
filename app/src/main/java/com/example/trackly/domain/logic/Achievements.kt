package com.example.trackly.domain.logic

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import java.time.LocalDate

enum class AchievementKind {
    /** The very first completed goal. */
    FIRST_STEP,

    /** Best streak of any habit. */
    STREAK,

    /** Completed daily goals, all habits together. */
    COMPLETIONS,

    /** Days on which every scheduled habit was done. */
    PERFECT_DAYS,

    /** Habits created (archived ones count too). */
    HABITS,

    /** Days with at least one completed goal. */
    ACTIVE_DAYS,
}

data class Achievement(val id: String, val emoji: String, val kind: AchievementKind, val target: Int)

data class AchievementProgress(val achievement: Achievement, val current: Int) {
    val unlocked: Boolean get() = current >= achievement.target
    val fraction: Float get() = (current.toFloat() / achievement.target).coerceIn(0f, 1f)
}

data class AchievementStats(
    val bestStreak: Int,
    val completions: Int,
    val perfectDays: Int,
    val habits: Int,
    val activeDays: Int,
) {
    fun valueOf(kind: AchievementKind): Int = when (kind) {
        AchievementKind.FIRST_STEP, AchievementKind.COMPLETIONS -> completions
        AchievementKind.STREAK -> bestStreak
        AchievementKind.PERFECT_DAYS -> perfectDays
        AchievementKind.HABITS -> habits
        AchievementKind.ACTIVE_DAYS -> activeDays
    }
}

/** Badges earned from the history the app already keeps: nothing extra is stored. */
object Achievements {

    val all: List<Achievement> = listOf(
        Achievement("first_step", "🌱", AchievementKind.FIRST_STEP, 1),
        Achievement("streak_3", "🔥", AchievementKind.STREAK, 3),
        Achievement("streak_7", "⚡", AchievementKind.STREAK, 7),
        Achievement("streak_21", "💪", AchievementKind.STREAK, 21),
        Achievement("streak_30", "🏅", AchievementKind.STREAK, 30),
        Achievement("streak_100", "💯", AchievementKind.STREAK, 100),
        Achievement("streak_365", "👑", AchievementKind.STREAK, 365),
        Achievement("done_10", "✅", AchievementKind.COMPLETIONS, 10),
        Achievement("done_50", "⭐", AchievementKind.COMPLETIONS, 50),
        Achievement("done_100", "🌟", AchievementKind.COMPLETIONS, 100),
        Achievement("done_500", "🚀", AchievementKind.COMPLETIONS, 500),
        Achievement("done_1000", "🏆", AchievementKind.COMPLETIONS, 1000),
        Achievement("perfect_1", "✨", AchievementKind.PERFECT_DAYS, 1),
        Achievement("perfect_7", "🌈", AchievementKind.PERFECT_DAYS, 7),
        Achievement("perfect_30", "💎", AchievementKind.PERFECT_DAYS, 30),
        Achievement("habits_3", "🧩", AchievementKind.HABITS, 3),
        Achievement("habits_5", "🎯", AchievementKind.HABITS, 5),
        Achievement("days_30", "📅", AchievementKind.ACTIVE_DAYS, 30),
        Achievement("days_100", "🗓️", AchievementKind.ACTIVE_DAYS, 100),
    )

    fun stats(habits: List<Habit>, entries: List<HabitEntry>, today: LocalDate): AchievementStats {
        val completed = entries.filter { it.completed && !it.date.isAfter(today) }
        val byHabit = entries.groupBy { it.habitId }
        val bestStreak = habits.maxOfOrNull { StreakCalculator.compute(it, byHabit[it.id].orEmpty(), today).best } ?: 0
        return AchievementStats(
            bestStreak = bestStreak,
            completions = completed.size,
            perfectDays = perfectDays(habits.filter { !it.isArchived }, completed, today),
            habits = habits.size,
            activeDays = completed.mapTo(HashSet()) { it.date }.size,
        )
    }

    fun progress(stats: AchievementStats): List<AchievementProgress> =
        all.map { AchievementProgress(it, stats.valueOf(it.kind)) }

    /** The locked achievement closest to being earned, or null when everything is unlocked. */
    fun next(progress: List<AchievementProgress>): AchievementProgress? =
        progress.filter { !it.unlocked }.maxWithOrNull(compareBy<AchievementProgress> { it.fraction }.thenBy { -it.achievement.target })

    /**
     * Days (up to [today]) on which at least one of [habits] was due and every due habit was done.
     * Archived habits are left out by the caller: the app does not know when they were archived.
     */
    fun perfectDays(habits: List<Habit>, completed: List<HabitEntry>, today: LocalDate): Int {
        if (habits.isEmpty()) return 0
        val doneByDate = HashMap<LocalDate, MutableSet<Long>>()
        for (entry in completed) doneByDate.getOrPut(entry.date) { HashSet() }.add(entry.habitId)
        var count = 0
        for ((date, done) in doneByDate) {
            if (date.isAfter(today)) continue
            val due = habits.filter { it.isActiveOn(date) }
            if (due.isNotEmpty() && due.all { it.id in done }) count++
        }
        return count
    }
}
