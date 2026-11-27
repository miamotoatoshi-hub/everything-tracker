package com.example.trackly.domain.logic

/** Streak lengths worth a celebration (and a share). */
object StreakMilestones {
    val days: List<Int> = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000)

    /**
     * The milestone a streak has just reached while growing from [before] to [after] (6 → 7 gives 7),
     * or null when no milestone was crossed or the streak did not grow.
     */
    fun reached(before: Int, after: Int): Int? =
        if (after <= before) null else days.lastOrNull { it in (before + 1)..after }

    /**
     * The current streak once today's goal is completed, given the streak shown before that.
     * [StreakCalculator] does not break a streak for a scheduled day that is still in progress,
     * so completing it adds exactly one day; an unscheduled day neither adds nor breaks.
     */
    fun afterCompletingToday(streakBefore: Int, scheduledToday: Boolean): Int =
        if (scheduledToday) streakBefore + 1 else streakBefore
}
