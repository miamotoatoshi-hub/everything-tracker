package com.example.trackly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.trackly.data.local.entity.HabitEntity
import com.example.trackly.data.local.entity.HabitPauseEntity
import com.example.trackly.data.local.entity.HabitReminderEntity
import com.example.trackly.data.local.entity.HabitScheduleEntity
import com.example.trackly.data.local.entity.HabitWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<HabitWithDetails>>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitWithDetails?>

    @Transaction
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<HabitWithDetails>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitWithDetails?

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getEntity(id: Long): HabitEntity?

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: HabitScheduleEntity)

    @Insert
    suspend fun insertReminders(reminders: List<HabitReminderEntity>)

    @Query("DELETE FROM habit_reminders WHERE habitId = :habitId")
    suspend fun deleteReminders(habitId: Long)

    @Insert
    suspend fun insertPause(pause: HabitPauseEntity): Long

    @Query("SELECT * FROM habit_pauses WHERE habitId = :habitId AND toEpochDay IS NULL")
    suspend fun getOpenPauses(habitId: Long): List<HabitPauseEntity>

    @Query("UPDATE habit_pauses SET toEpochDay = :toEpochDay WHERE id = :id")
    suspend fun closePause(id: Long, toEpochDay: Long)

    @Query("DELETE FROM habit_pauses WHERE id = :id")
    suspend fun deletePause(id: Long)

    @Query("UPDATE habits SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE habits SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int, now: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM habits")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM habits WHERE isArchived = 0")
    suspend fun countNonArchived(): Int
}
