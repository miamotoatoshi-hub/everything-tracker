package com.example.trackly.widget

import com.example.trackly.di.AppContainer
import com.example.trackly.domain.logic.EntitlementResolver
import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.logic.StreakCalculator
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.HabitType
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** One habit row on the widget. */
data class WidgetHabit(
    val id: Long,
    val name: String,
    val icon: String,
    val colorArgb: Int,
    val isBoolean: Boolean,
    val value: Double,
    val goal: Double,
    val completed: Boolean,
    val streak: Int,
)

/** Everything the "Today" widget shows. */
data class TodayWidgetState(
    val isPremium: Boolean,
    val habits: List<WidgetHabit>,
) {
    val completed: Int get() = habits.count { it.completed }
    val percent: Int get() = if (habits.isEmpty()) 0 else completed * 100 / habits.size
}

object TodayWidgetData {

    /** Today's scheduled habits, pending ones first, in the user's order. Pure, so it is unit-tested. */
    fun build(habits: List<Habit>, entries: List<HabitEntry>, today: LocalDate, isPremium: Boolean): TodayWidgetState {
        val byHabit = entries.groupBy { it.habitId }
        val rows = habits
            .filter { !it.isArchived && it.isActiveOn(today) }
            .map { habit ->
                val own = byHabit[habit.id].orEmpty()
                val entry = own.firstOrNull { it.date == today }
                val value = entry?.value ?: 0.0
                WidgetHabit(
                    id = habit.id,
                    name = habit.name,
                    icon = habit.icon,
                    colorArgb = habit.colorArgb,
                    isBoolean = habit.type == HabitType.BOOLEAN,
                    value = value,
                    goal = habit.goalValue,
                    completed = entry?.completed == true || GoalProgress.isComplete(value, habit.goalValue),
                    streak = StreakCalculator.compute(habit, own, today).current,
                )
            }
            .sortedBy { it.completed } // stable: keeps the user's order within each group
        return TodayWidgetState(isPremium = isPremium, habits = rows)
    }

    suspend fun load(container: AppContainer): TodayWidgetState {
        val today = LocalDate.now(container.clock)
        // In a cold process Google Play has not answered yet: fall back to the last verified status.
        val cached = try {
            EntitlementResolver.whenUnverifiable(container.settingsRepository.loadCachedEntitlement(), container.clock.millis())
        } catch (error: Exception) {
            null
        }
        val premium = container.isPremium.value || cached?.isPremium == true
        return build(
            habits = container.habitRepository.getHabits(),
            entries = container.habitRepository.observeEntries().first(),
            today = today,
            isPremium = premium,
        )
    }
}
