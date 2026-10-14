package com.example.trackly.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colorArgb: Int,
    val type: String,
    val goalValue: Double,
    val unit: String,
    val customUnit: String?,
    val stepValue: Double,
    val timeOfDay: String,
    val startEpochDay: Long,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "habit_schedules",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HabitScheduleEntity(
    @PrimaryKey val habitId: Long,
    val type: String,
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    val daysMask: Int,
    val intervalDays: Int,
)

@Entity(
    tableName = "habit_reminders",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class HabitReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val minuteOfDay: Int,
    val message: String?,
    val enabled: Boolean,
    val repeatRule: String,
)

@Entity(
    tableName = "habit_pauses",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class HabitPauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val fromEpochDay: Long,
    /** Null while the habit is still paused. */
    val toEpochDay: Long?,
)

/** Progress of one habit on one day (value + whether the goal was reached). */
@Entity(
    tableName = "habit_completions",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "epochDay"], unique = true)],
)
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val value: Double,
    /** Goal in effect when the value was logged. */
    val goal: Double,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class HabitWithDetails(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val schedule: HabitScheduleEntity?,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val reminders: List<HabitReminderEntity>,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val pauses: List<HabitPauseEntity>,
)
