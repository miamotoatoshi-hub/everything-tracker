package com.example.trackly.domain

import com.example.trackly.domain.logic.StatsPeriod
import com.example.trackly.domain.model.AccentTheme
import com.example.trackly.domain.model.FeatureGate
import com.example.trackly.domain.model.FreeTier
import com.example.trackly.domain.model.HabitColors
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.PremiumPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PremiumPolicyTest {
    private val free = PremiumPolicy(isPremium = false)
    private val premium = PremiumPolicy(isPremium = true)
    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun `free plan allows up to five active habits`() {
        assertTrue(free.canCreateHabit(0))
        assertTrue(free.canCreateHabit(FreeTier.MAX_ACTIVE_HABITS - 1))
        assertFalse(free.canCreateHabit(FreeTier.MAX_ACTIVE_HABITS))
        assertEquals(0, free.remainingHabitSlots(7))
        assertEquals(2, free.remainingHabitSlots(3))
    }

    @Test
    fun `premium has no habit limit`() {
        assertTrue(premium.canCreateHabit(500))
    }

    @Test
    fun `reminder limit per habit`() {
        assertTrue(free.allowsReminderCount(FreeTier.MAX_REMINDERS_PER_HABIT))
        assertFalse(free.allowsReminderCount(FreeTier.MAX_REMINDERS_PER_HABIT + 1))
        assertTrue(premium.allowsReminderCount(50))
    }

    @Test
    fun `free colours are limited to the base palette`() {
        assertTrue(free.isColorAllowed(HabitColors.FREE.first()))
        assertFalse(free.isColorAllowed(HabitColors.PREMIUM.first()))
        assertFalse(free.isColorAllowed(0xFF123456.toInt()))
        assertTrue(premium.isColorAllowed(0xFF123456.toInt()))
    }

    @Test
    fun `premium accents are locked for the free plan`() {
        assertTrue(free.isAccentAllowed(AccentTheme.INDIGO))
        assertTrue(free.isAccentAllowed(AccentTheme.TEAL))
        AccentTheme.entries.filter { it.requiresPremium }.forEach {
            assertFalse(free.isAccentAllowed(it))
            assertTrue(premium.isAccentAllowed(it))
        }
    }

    @Test
    fun `free statistics are limited to the week view`() {
        assertTrue(free.isStatsPeriodAllowed(StatsPeriod.WEEK))
        assertFalse(free.isStatsPeriodAllowed(StatsPeriod.MONTH))
        assertFalse(free.isStatsPeriodAllowed(StatsPeriod.YEAR))
        StatsPeriod.entries.forEach { assertTrue(premium.isStatsPeriodAllowed(it)) }
    }

    @Test
    fun `history and calendar windows`() {
        assertEquals(today.minusDays(29), free.earliestHistoryDate(today))
        assertNull(premium.earliestHistoryDate(today))
        assertEquals(YearMonth.of(2026, 8), free.earliestCalendarMonth(today))
        assertNull(premium.earliestCalendarMonth(today))
    }

    @Test
    fun `gate reports the locked feature`() {
        assertEquals(FeatureGate.Allowed, premium.gate(PremiumFeature.PREMIUM_INSIGHTS))
        assertEquals(
            FeatureGate.Locked(PremiumFeature.PREMIUM_INSIGHTS),
            free.gate(PremiumFeature.PREMIUM_INSIGHTS),
        )
    }

    @Test
    fun `every feature is locked for free and unlocked for premium`() {
        PremiumFeature.entries.forEach {
            assertFalse(free.isUnlocked(it))
            assertTrue(premium.isUnlocked(it))
        }
    }
}
