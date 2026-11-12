package com.example.trackly.data.backup

import androidx.room.withTransaction
import com.example.trackly.data.local.TracklyDatabase
import com.example.trackly.domain.repository.BackupImportResult
import com.example.trackly.domain.repository.BackupRepository
import java.time.Clock

/**
 * Room-backed [BackupRepository]. A restore replaces all data in one transaction: if anything fails,
 * the user's current data is left untouched.
 *
 * @param onDataReplaced re-arms reminders (and refreshes widgets) after a restore.
 */
class RoomBackupRepository(
    private val database: TracklyDatabase,
    private val clock: Clock,
    private val appVersion: String,
    private val onDataReplaced: suspend () -> Unit = {},
) : BackupRepository {

    private val habitDao = database.habitDao()
    private val completionDao = database.completionDao()

    override suspend fun exportBackup(): String {
        val (habits, entries) = database.withTransaction { habitDao.getAll() to completionDao.getAll() }
        return BackupCodec.encode(habits, entries, clock.millis(), appVersion)
    }

    override suspend fun importBackup(json: String): BackupImportResult {
        val restorable = when (val decoded = BackupCodec.decode(json)) {
            is DecodeResult.Invalid -> return BackupImportResult.Invalid
            is DecodeResult.TooNew -> return BackupImportResult.TooNew
            is DecodeResult.Ok -> decoded.habits
        }
        database.withTransaction {
            habitDao.deleteAll() // child tables are removed by ON DELETE CASCADE
            restorable.forEach { item ->
                val id = habitDao.insertHabit(item.habit.copy(id = 0))
                item.schedule?.let { habitDao.upsertSchedule(it.copy(habitId = id)) }
                if (item.reminders.isNotEmpty()) habitDao.insertReminders(item.reminders.map { it.copy(id = 0, habitId = id) })
                if (item.pauses.isNotEmpty()) habitDao.insertPauses(item.pauses.map { it.copy(id = 0, habitId = id) })
                if (item.entries.isNotEmpty()) completionDao.insertAll(item.entries.map { it.copy(id = 0, habitId = id) })
            }
        }
        try {
            onDataReplaced()
        } catch (ignored: Exception) {
            // Reminders are re-synchronised again on the next start; the restore itself succeeded.
        }
        return BackupImportResult.Success(habits = restorable.size, entries = restorable.sumOf { it.entries.size })
    }

    override suspend fun exportCsv(): String {
        val (habits, entries) = database.withTransaction { habitDao.getAll() to completionDao.getAll() }
        return BackupCodec.csv(habits, entries)
    }
}
