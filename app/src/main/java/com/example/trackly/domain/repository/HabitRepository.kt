package com.example.trackly.domain.repository

import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitRepository {
    /** All habits (including archived), ordered by the user's sort order. */
    fun observeHabits(): Flow<List<Habit>>

    fun observeHabit(id: Long): Flow<Habit?>

    /** Every logged entry of every habit. */
    fun observeEntries(): Flow<List<HabitEntry>>

    fun observeEntries(habitId: Long): Flow<List<HabitEntry>>

    suspend fun getHabit(id: Long): Habit?

    suspend fun getHabits(): List<Habit>

    suspend fun getEntry(habitId: Long, date: LocalDate): HabitEntry?

    /** Persists a new habit and returns its id. The draft must already be validated. */
    suspend fun createHabit(draft: HabitDraft): Long

    suspend fun updateHabit(draft: HabitDraft)

    /** Sets the logged value for a day; a value of 0 removes the entry. */
    suspend fun setEntryValue(habitId: Long, date: LocalDate, value: Double): HabitEntry?

    /** Adds [delta] (may be negative) to the day's value and returns the stored entry, or null if it became empty. */
    suspend fun addToEntry(habitId: Long, date: LocalDate, delta: Double): HabitEntry?

    suspend fun setArchived(habitId: Long, archived: Boolean)

    suspend fun setPaused(habitId: Long, paused: Boolean, today: LocalDate)

    suspend fun deleteHabit(habitId: Long)

    /** Persists a new ordering; ids not present keep their relative order after the listed ones. */
    suspend fun updateOrder(orderedIds: List<Long>)

    suspend fun countNonArchived(): Int
}
