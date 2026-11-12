package com.example.trackly.data

import com.example.trackly.data.backup.BackupCodec
import com.example.trackly.data.backup.DecodeResult
import com.example.trackly.data.local.entity.HabitCompletionEntity
import com.example.trackly.data.local.entity.HabitEntity
import com.example.trackly.data.local.entity.HabitPauseEntity
import com.example.trackly.data.local.entity.HabitReminderEntity
import com.example.trackly.data.local.entity.HabitScheduleEntity
import com.example.trackly.data.local.entity.HabitWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun habit(id: Long, name: String = "Water", goal: Double = 8.0) = HabitWithDetails(
        habit = HabitEntity(
            id = id,
            name = name,
            icon = "💧",
            colorArgb = 0xFF2196F3.toInt(),
            type = "COUNTER",
            goalValue = goal,
            unit = "GLASSES",
            customUnit = null,
            stepValue = 1.0,
            timeOfDay = "ANYTIME",
            startEpochDay = 20_000,
            isArchived = false,
            sortOrder = id.toInt(),
            createdAt = 1L,
            updatedAt = 2L,
        ),
        schedule = HabitScheduleEntity(id, "SPECIFIC_DAYS", 0b0011111, 1),
        reminders = listOf(HabitReminderEntity(10, id, 8 * 60, "Drink!", true, "HABIT_SCHEDULE")),
        pauses = listOf(HabitPauseEntity(5, id, 20_010, 20_012)),
    )

    private fun entry(habitId: Long, day: Long, value: Double) =
        HabitCompletionEntity(0, habitId, day, value, 8.0, value >= 8.0, 3L, 4L)

    @Test
    fun `a backup round-trips every table`() {
        val habits = listOf(habit(1), habit(2, name = "Read, \"daily\""))
        val entries = listOf(entry(1, 20_001, 8.0), entry(1, 20_002, 3.0), entry(2, 20_001, 1.0))
        val json = BackupCodec.encode(habits, entries, exportedAtMillis = 99L, appVersion = "1.1.0")

        val decoded = BackupCodec.decode(json) as DecodeResult.Ok
        assertEquals(2, decoded.habits.size)
        val first = decoded.habits[0]
        assertEquals("Water", first.habit.name)
        assertEquals(8.0, first.habit.goalValue, 0.0)
        assertEquals(0b0011111, first.schedule?.daysMask)
        assertEquals("Drink!", first.reminders.single().message)
        assertEquals(20_012L, first.pauses.single().toEpochDay)
        assertEquals(listOf(20_001L, 20_002L), first.entries.map { it.epochDay }.sorted())
        assertEquals("Read, \"daily\"", decoded.habits[1].habit.name)
        // Ids are assigned by the database on restore.
        assertTrue(decoded.habits.all { it.habit.id == 0L })
    }

    @Test
    fun `garbage and foreign json are rejected`() {
        assertEquals(DecodeResult.Invalid, BackupCodec.decode("not json"))
        assertEquals(DecodeResult.Invalid, BackupCodec.decode("""{"hello": 1}"""))
        assertEquals(
            DecodeResult.Invalid,
            BackupCodec.decode("""{"format":"other-app","version":1,"exportedAtMillis":1,"appVersion":"x","habits":[]}"""),
        )
    }

    @Test
    fun `backups from a newer app version are refused`() {
        val json = """{"format":"trackly-backup","version":99,"exportedAtMillis":1,"appVersion":"9","habits":[]}"""
        assertEquals(DecodeResult.TooNew, BackupCodec.decode(json))
    }

    @Test
    fun `impossible values reject the whole backup`() {
        val good = BackupCodec.encode(listOf(habit(1)), emptyList(), 1L, "1")
        val broken = good.replace("\"goalValue\": 8.0", "\"goalValue\": -1.0")
        assertTrue(good != broken)
        assertEquals(DecodeResult.Invalid, BackupCodec.decode(broken))
    }

    @Test
    fun `csv has one row per logged day and is spreadsheet safe`() {
        val habits = listOf(habit(1), habit(2, name = "=HYPERLINK(\"x\")"))
        val csv = BackupCodec.csv(habits, listOf(entry(2, 20_001, 1.0), entry(1, 20_001, 8.0), entry(1, 20_000, 2.5)))
        val lines = csv.trimEnd().lines()
        assertEquals("date,habit,value,goal,unit,completed", lines[0])
        assertEquals("2024-10-04,Water,2.5,8,glasses,no", lines[1])
        assertEquals("2024-10-05,Water,8,8,glasses,yes", lines[2])
        // Formula-looking names are defused and quoted.
        assertEquals("2024-10-05,\"'=HYPERLINK(\"\"x\"\")\",1,8,glasses,no", lines[3])
    }
}
