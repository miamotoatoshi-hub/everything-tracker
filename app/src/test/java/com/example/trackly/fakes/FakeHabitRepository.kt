package com.example.trackly.fakes

import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.model.PauseRange
import com.example.trackly.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Small in-memory repository for use-case and view-model tests. */
class FakeHabitRepository(initialHabits: List<Habit> = emptyList()) : HabitRepository {
    val habits = MutableStateFlow(initialHabits)
    val entries = MutableStateFlow<List<HabitEntry>>(emptyList())
    private var nextId = (initialHabits.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeHabits(): Flow<List<Habit>> = habits
    override fun observeHabit(id: Long): Flow<Habit?> = habits.map { list -> list.firstOrNull { it.id == id } }
    override fun observeEntries(): Flow<List<HabitEntry>> = entries
    override fun observeEntries(habitId: Long): Flow<List<HabitEntry>> =
        entries.map { list -> list.filter { it.habitId == habitId } }

    override suspend fun getHabit(id: Long): Habit? = habits.value.firstOrNull { it.id == id }
    override suspend fun getHabits(): List<Habit> = habits.value
    override suspend fun getEntry(habitId: Long, date: LocalDate): HabitEntry? =
        entries.value.firstOrNull { it.habitId == habitId && it.date == date }

    override suspend fun createHabit(draft: HabitDraft): Long {
        val id = nextId++
        habits.value = habits.value + Fixtures.habit(
            id = id,
            name = draft.name,
            type = draft.type,
            goal = draft.goalValue,
            unit = draft.unit,
            schedule = draft.schedule,
            start = draft.startDate,
            reminders = draft.reminders,
            color = draft.colorArgb,
            sortOrder = habits.value.size,
        )
        return id
    }

    override suspend fun updateHabit(draft: HabitDraft) {
        habits.value = habits.value.map {
            if (it.id == draft.id) {
                it.copy(
                    name = draft.name,
                    colorArgb = draft.colorArgb,
                    type = draft.type,
                    goalValue = draft.goalValue,
                    unit = draft.unit,
                    schedule = draft.schedule,
                    startDate = draft.startDate,
                    reminders = draft.reminders,
                )
            } else {
                it
            }
        }
    }

    override suspend fun setEntryValue(habitId: Long, date: LocalDate, value: Double): HabitEntry? {
        val habit = getHabit(habitId) ?: return null
        val others = entries.value.filterNot { it.habitId == habitId && it.date == date }
        if (value <= 0.0) {
            entries.value = others
            return null
        }
        val entry = HabitEntry(
            habitId, date, value, habit.goalValue, GoalProgress.isComplete(value, habit.goalValue),
        )
        entries.value = others + entry
        return entry
    }

    override suspend fun addToEntry(habitId: Long, date: LocalDate, delta: Double): HabitEntry? {
        val current = getEntry(habitId, date)?.value ?: 0.0
        return setEntryValue(habitId, date, GoalProgress.applyDelta(current, delta))
    }

    override suspend fun setArchived(habitId: Long, archived: Boolean) {
        habits.value = habits.value.map { if (it.id == habitId) it.copy(isArchived = archived) else it }
    }

    override suspend fun setPaused(habitId: Long, paused: Boolean, today: LocalDate) {
        habits.value = habits.value.map {
            if (it.id != habitId) it else if (paused) {
                it.copy(pauses = it.pauses + PauseRange(today, null))
            } else {
                it.copy(pauses = it.pauses.map { p -> if (p.to == null) p.copy(to = today.minusDays(1)) else p })
            }
        }
    }

    override suspend fun deleteHabit(habitId: Long) {
        habits.value = habits.value.filterNot { it.id == habitId }
        entries.value = entries.value.filterNot { it.habitId == habitId }
    }

    override suspend fun updateOrder(orderedIds: List<Long>) {
        val order = orderedIds.withIndex().associate { it.value to it.index }
        habits.value = habits.value
            .map { if (it.id in order) it.copy(sortOrder = order.getValue(it.id)) else it }
            .sortedBy { it.sortOrder }
    }

    override suspend fun countNonArchived(): Int = habits.value.count { !it.isArchived }
}
