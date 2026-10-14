package com.example.trackly.data.repository

import androidx.room.withTransaction
import com.example.trackly.data.local.TracklyDatabase
import com.example.trackly.data.local.entity.HabitCompletionEntity
import com.example.trackly.data.local.entity.HabitPauseEntity
import com.example.trackly.data.mapper.toDomain
import com.example.trackly.data.mapper.toEntity
import com.example.trackly.domain.logic.GoalProgress
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitDraft
import com.example.trackly.domain.model.HabitEntry
import com.example.trackly.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * Room-backed [HabitRepository].
 *
 * @param onHabitsChanged invoked after anything that can affect reminders (create, edit, pause,
 * archive, delete) so alarms are re-synchronised.
 */
class HabitRepositoryImpl(
    private val database: TracklyDatabase,
    private val clock: Clock,
    private val onHabitsChanged: suspend () -> Unit = {},
) : HabitRepository {

    private val habitDao = database.habitDao()
    private val completionDao = database.completionDao()

    override fun observeHabits(): Flow<List<Habit>> =
        habitDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeHabit(id: Long): Flow<Habit?> =
        habitDao.observeById(id).map { it?.toDomain() }

    override fun observeEntries(): Flow<List<HabitEntry>> =
        completionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEntries(habitId: Long): Flow<List<HabitEntry>> =
        completionDao.observeForHabit(habitId).map { list -> list.map { it.toDomain() } }

    override suspend fun getHabit(id: Long): Habit? = habitDao.getById(id)?.toDomain()

    override suspend fun getHabits(): List<Habit> = habitDao.getAll().map { it.toDomain() }

    override suspend fun getEntry(habitId: Long, date: LocalDate): HabitEntry? =
        completionDao.get(habitId, date.toEpochDay())?.toDomain()

    override suspend fun createHabit(draft: HabitDraft): Long {
        val now = clock.millis()
        val id = database.withTransaction {
            val sortOrder = habitDao.maxSortOrder() + 1
            val newId = habitDao.insertHabit(draft.toEntity(sortOrder, now, now).copy(id = 0))
            habitDao.upsertSchedule(draft.schedule.toEntity(newId))
            if (draft.reminders.isNotEmpty()) {
                habitDao.insertReminders(draft.reminders.map { it.copy(id = 0).toEntity(newId) })
            }
            newId
        }
        onHabitsChanged()
        return id
    }

    override suspend fun updateHabit(draft: HabitDraft) {
        val now = clock.millis()
        database.withTransaction {
            val existing = habitDao.getEntity(draft.id) ?: return@withTransaction
            habitDao.updateHabit(
                draft.toEntity(
                    sortOrder = existing.sortOrder,
                    createdAt = existing.createdAt,
                    updatedAt = now,
                    isArchived = existing.isArchived,
                ),
            )
            habitDao.upsertSchedule(draft.schedule.toEntity(draft.id))
            habitDao.deleteReminders(draft.id)
            if (draft.reminders.isNotEmpty()) {
                habitDao.insertReminders(draft.reminders.map { it.toEntity(draft.id) })
            }
            // The goal may have changed: re-evaluate today's completion against the new goal.
            val today = LocalDate.now(clock).toEpochDay()
            completionDao.get(draft.id, today)?.let { entry ->
                completionDao.upsert(
                    entry.copy(
                        goal = draft.goalValue,
                        completed = GoalProgress.isComplete(entry.value, draft.goalValue),
                        updatedAt = now,
                    ),
                )
            }
        }
        onHabitsChanged()
    }

    override suspend fun setEntryValue(habitId: Long, date: LocalDate, value: Double): HabitEntry? =
        database.withTransaction { writeEntry(habitId, date) { value } }

    override suspend fun addToEntry(habitId: Long, date: LocalDate, delta: Double): HabitEntry? =
        database.withTransaction { writeEntry(habitId, date) { current -> GoalProgress.applyDelta(current, delta) } }

    private suspend fun writeEntry(habitId: Long, date: LocalDate, compute: (Double) -> Double): HabitEntry? {
        val habit = habitDao.getEntity(habitId) ?: return null
        val epochDay = date.toEpochDay()
        val existing = completionDao.get(habitId, epochDay)
        val newValue = compute(existing?.value ?: 0.0).coerceAtLeast(0.0)
        if (newValue <= 0.0) {
            completionDao.delete(habitId, epochDay)
            return null
        }
        val now = clock.millis()
        val entity = HabitCompletionEntity(
            id = existing?.id ?: 0,
            habitId = habitId,
            epochDay = epochDay,
            value = newValue,
            goal = habit.goalValue,
            completed = GoalProgress.isComplete(newValue, habit.goalValue),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        completionDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun setArchived(habitId: Long, archived: Boolean) {
        habitDao.setArchived(habitId, archived, clock.millis())
        onHabitsChanged()
    }

    override suspend fun setPaused(habitId: Long, paused: Boolean, today: LocalDate) {
        database.withTransaction {
            val open = habitDao.getOpenPauses(habitId)
            if (paused) {
                if (open.isEmpty()) {
                    habitDao.insertPause(HabitPauseEntity(habitId = habitId, fromEpochDay = today.toEpochDay(), toEpochDay = null))
                }
            } else {
                for (pause in open) {
                    if (pause.fromEpochDay >= today.toEpochDay()) {
                        habitDao.deletePause(pause.id)
                    } else {
                        habitDao.closePause(pause.id, today.toEpochDay() - 1)
                    }
                }
            }
        }
        onHabitsChanged()
    }

    override suspend fun deleteHabit(habitId: Long) {
        habitDao.deleteById(habitId)
        onHabitsChanged()
    }

    override suspend fun updateOrder(orderedIds: List<Long>) {
        val now = clock.millis()
        database.withTransaction {
            orderedIds.forEachIndexed { index, id -> habitDao.setSortOrder(id, index, now) }
        }
    }

    override suspend fun countNonArchived(): Int = habitDao.countNonArchived()
}
