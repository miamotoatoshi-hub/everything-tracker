package com.example.trackly.domain.model

import com.example.trackly.domain.logic.StatsPeriod
import java.time.LocalDate
import java.time.YearMonth

/** Every capability that is gated behind Premium. Add a new entry here to gate a new feature. */
enum class PremiumFeature {
    UNLIMITED_HABITS,
    ADVANCED_STATISTICS,
    ADVANCED_CHARTS,
    UNLIMITED_REMINDERS,
    CUSTOM_COLORS,
    ADVANCED_CALENDAR,
    FULL_HISTORY,
    PREMIUM_THEMES,
    PREMIUM_INSIGHTS,
}

/** Limits of the free plan. Change them here — nothing else in the app hard-codes them. */
object FreeTier {
    const val MAX_ACTIVE_HABITS = 5
    const val MAX_REMINDERS_PER_HABIT = 2
    const val HISTORY_DAYS = 30
    const val CALENDAR_MONTHS_BACK = 2
    val STATS_PERIODS: Set<StatsPeriod> = setOf(StatsPeriod.WEEK)
}

/** Result of asking whether a feature may be used. */
sealed interface FeatureGate {
    data object Allowed : FeatureGate
    data class Locked(val feature: PremiumFeature) : FeatureGate
}

/** Decides what the free plan vs Premium may do. Pure and trivially testable. */
data class PremiumPolicy(val isPremium: Boolean) {

    fun gate(feature: PremiumFeature): FeatureGate =
        if (isPremium) FeatureGate.Allowed else FeatureGate.Locked(feature)

    fun isUnlocked(feature: PremiumFeature): Boolean = isPremium

    fun canCreateHabit(activeHabitCount: Int): Boolean =
        isPremium || activeHabitCount < FreeTier.MAX_ACTIVE_HABITS

    fun remainingHabitSlots(activeHabitCount: Int): Int =
        if (isPremium) Int.MAX_VALUE else (FreeTier.MAX_ACTIVE_HABITS - activeHabitCount).coerceAtLeast(0)

    /** Whether a habit may carry [count] reminders in total. */
    fun allowsReminderCount(count: Int): Boolean =
        isPremium || count <= FreeTier.MAX_REMINDERS_PER_HABIT

    fun isColorAllowed(colorArgb: Int): Boolean = isPremium || HabitColors.isFree(colorArgb)

    fun isAccentAllowed(accent: AccentTheme): Boolean = isPremium || !accent.requiresPremium

    fun isStatsPeriodAllowed(period: StatsPeriod): Boolean = isPremium || period in FreeTier.STATS_PERIODS

    /** Oldest date whose history is visible, or null when the whole history is available. */
    fun earliestHistoryDate(today: LocalDate): LocalDate? =
        if (isPremium) null else today.minusDays((FreeTier.HISTORY_DAYS - 1).toLong())

    /** Oldest month the calendar may navigate to, or null when unrestricted. */
    fun earliestCalendarMonth(today: LocalDate): YearMonth? =
        if (isPremium) null else YearMonth.from(today).minusMonths(FreeTier.CALENDAR_MONTHS_BACK.toLong())
}
