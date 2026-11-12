package com.example.trackly.data.backup

import com.example.trackly.data.local.entity.HabitCompletionEntity
import com.example.trackly.data.local.entity.HabitEntity
import com.example.trackly.data.local.entity.HabitPauseEntity
import com.example.trackly.data.local.entity.HabitReminderEntity
import com.example.trackly.data.local.entity.HabitScheduleEntity
import com.example.trackly.data.local.entity.HabitWithDetails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The backup file format. It mirrors the database tables, so a restore is lossless.
 * Bump [BackupFile.CURRENT_VERSION] (and keep reading older versions) whenever the format changes.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val exportedAtMillis: Long,
    val appVersion: String,
    val habits: List<BackupHabit>,
) {
    companion object {
        const val FORMAT = "trackly-backup"
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupHabit(
    val name: String,
    val icon: String,
    val colorArgb: Int,
    val type: String,
    val goalValue: Double,
    val unit: String,
    val customUnit: String? = null,
    val stepValue: Double,
    val timeOfDay: String,
    val startEpochDay: Long,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val schedule: BackupSchedule? = null,
    val reminders: List<BackupReminder> = emptyList(),
    val pauses: List<BackupPause> = emptyList(),
    val entries: List<BackupEntry> = emptyList(),
)

@Serializable
data class BackupSchedule(val type: String, val daysMask: Int, val intervalDays: Int)

@Serializable
data class BackupReminder(
    val minuteOfDay: Int,
    val message: String? = null,
    val enabled: Boolean = true,
    val repeatRule: String,
)

@Serializable
data class BackupPause(val fromEpochDay: Long, val toEpochDay: Long? = null)

@Serializable
data class BackupEntry(
    @SerialName("day") val epochDay: Long,
    val value: Double,
    val goal: Double,
    val completed: Boolean,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** A backup decoded into rows ready to insert (ids are assigned by the database). */
data class RestorableHabit(
    val habit: HabitEntity,
    val schedule: HabitScheduleEntity?,
    val reminders: List<HabitReminderEntity>,
    val pauses: List<HabitPauseEntity>,
    val entries: List<HabitCompletionEntity>,
)

sealed interface DecodeResult {
    data class Ok(val habits: List<RestorableHabit>) : DecodeResult
    data object Invalid : DecodeResult
    data object TooNew : DecodeResult
}

/** Pure encoding/decoding of backups and CSV; no Android or database access, so it is unit-tested on the JVM. */
object BackupCodec {
    private const val MAX_NAME = 60
    private const val MAX_TEXT = 200
    private const val MAX_HABITS = 10_000
    private const val MINUTES_PER_DAY = 24 * 60

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // newer minor fields are skipped by older apps
        encodeDefaults = true
    }

    fun encode(
        habits: List<HabitWithDetails>,
        entries: List<HabitCompletionEntity>,
        exportedAtMillis: Long,
        appVersion: String,
    ): String {
        val byHabit = entries.groupBy { it.habitId }
        val file = BackupFile(
            exportedAtMillis = exportedAtMillis,
            appVersion = appVersion,
            habits = habits.map { details ->
                val h = details.habit
                BackupHabit(
                    name = h.name,
                    icon = h.icon,
                    colorArgb = h.colorArgb,
                    type = h.type,
                    goalValue = h.goalValue,
                    unit = h.unit,
                    customUnit = h.customUnit,
                    stepValue = h.stepValue,
                    timeOfDay = h.timeOfDay,
                    startEpochDay = h.startEpochDay,
                    isArchived = h.isArchived,
                    sortOrder = h.sortOrder,
                    createdAt = h.createdAt,
                    updatedAt = h.updatedAt,
                    schedule = details.schedule?.let { BackupSchedule(it.type, it.daysMask, it.intervalDays) },
                    reminders = details.reminders.map {
                        BackupReminder(it.minuteOfDay, it.message, it.enabled, it.repeatRule)
                    },
                    pauses = details.pauses.map { BackupPause(it.fromEpochDay, it.toEpochDay) },
                    entries = byHabit[h.id].orEmpty().sortedBy { it.epochDay }.map {
                        BackupEntry(it.epochDay, it.value, it.goal, it.completed, it.createdAt, it.updatedAt)
                    },
                )
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    fun decode(text: String): DecodeResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (error: SerializationException) {
            return DecodeResult.Invalid
        } catch (error: IllegalArgumentException) {
            return DecodeResult.Invalid
        }
        if (file.format != BackupFile.FORMAT) return DecodeResult.Invalid
        if (file.version > BackupFile.CURRENT_VERSION) return DecodeResult.TooNew
        if (file.habits.size > MAX_HABITS) return DecodeResult.Invalid
        val habits = file.habits.map { toRestorable(it) ?: return DecodeResult.Invalid }
        return DecodeResult.Ok(habits)
    }

    /** Validates one habit; returns null if any value is impossible (the whole restore is then refused). */
    private fun toRestorable(b: BackupHabit): RestorableHabit? {
        val name = b.name.trim().take(MAX_NAME)
        if (name.isEmpty()) return null
        if (!b.goalValue.isFinite() || b.goalValue <= 0.0) return null
        if (!b.stepValue.isFinite() || b.stepValue <= 0.0) return null
        if (b.reminders.any { it.minuteOfDay !in 0 until MINUTES_PER_DAY }) return null
        if (b.entries.any { !it.value.isFinite() || it.value < 0.0 || !it.goal.isFinite() }) return null
        val habit = HabitEntity(
            id = 0,
            name = name,
            icon = b.icon.take(16),
            colorArgb = b.colorArgb,
            type = b.type,
            goalValue = b.goalValue,
            unit = b.unit,
            customUnit = b.customUnit?.take(MAX_NAME),
            stepValue = b.stepValue,
            timeOfDay = b.timeOfDay,
            startEpochDay = b.startEpochDay,
            isArchived = b.isArchived,
            sortOrder = b.sortOrder,
            createdAt = b.createdAt,
            updatedAt = b.updatedAt,
        )
        return RestorableHabit(
            habit = habit,
            schedule = b.schedule?.let {
                HabitScheduleEntity(habitId = 0, type = it.type, daysMask = it.daysMask and 0x7F, intervalDays = it.intervalDays.coerceAtLeast(1))
            },
            reminders = b.reminders.map {
                HabitReminderEntity(
                    habitId = 0,
                    minuteOfDay = it.minuteOfDay,
                    message = it.message?.take(MAX_TEXT),
                    enabled = it.enabled,
                    repeatRule = it.repeatRule,
                )
            },
            pauses = b.pauses.map { HabitPauseEntity(habitId = 0, fromEpochDay = it.fromEpochDay, toEpochDay = it.toEpochDay) },
            // One entry per day: the last one wins if a damaged file repeats a day.
            entries = b.entries.associateBy { it.epochDay }.values.map {
                HabitCompletionEntity(
                    habitId = 0,
                    epochDay = it.epochDay,
                    value = it.value,
                    goal = it.goal,
                    completed = it.completed,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
        )
    }

    /** "date,habit,value,goal,unit,completed" — one row per logged day, oldest first. */
    fun csv(habits: List<HabitWithDetails>, entries: List<HabitCompletionEntity>): String {
        val byId = habits.associateBy { it.habit.id }
        val builder = StringBuilder("date,habit,value,goal,unit,completed\n")
        entries
            .filter { it.habitId in byId }
            .sortedWith(compareBy({ it.epochDay }, { byId.getValue(it.habitId).habit.sortOrder }))
            .forEach { entry ->
                val habit = byId.getValue(entry.habitId).habit
                val unit = habit.customUnit?.takeIf { it.isNotBlank() } ?: habit.unit.lowercase()
                builder
                    .append(LocalDate.ofEpochDay(entry.epochDay)).append(',')
                    .append(csvField(habit.name)).append(',')
                    .append(number(entry.value)).append(',')
                    .append(number(entry.goal)).append(',')
                    .append(csvField(unit)).append(',')
                    .append(if (entry.completed) "yes" else "no")
                    .append('\n')
            }
        return builder.toString()
    }

    private fun number(value: Double): String =
        if (value == Math.floor(value) && value < 1e15) value.toLong().toString() else value.toString()

    /** Quotes a CSV field when needed; also defuses spreadsheet formula injection ("=", "+", "-", "@"). */
    internal fun csvField(raw: String): String {
        val safe = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
