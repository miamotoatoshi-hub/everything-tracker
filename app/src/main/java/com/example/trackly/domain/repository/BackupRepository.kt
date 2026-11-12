package com.example.trackly.domain.repository

/** Outcome of restoring a backup file. */
sealed interface BackupImportResult {
    data class Success(val habits: Int, val entries: Int) : BackupImportResult

    /** Not a Trackly backup, or damaged. Nothing was changed. */
    data object Invalid : BackupImportResult

    /** Made by a newer version of Trackly. Nothing was changed. */
    data object TooNew : BackupImportResult
}

/** Export and restore of all habit data, so users can move to a new phone or keep their own copy. */
interface BackupRepository {
    /** The complete data set as a JSON backup document. */
    suspend fun exportBackup(): String

    /** Replaces all current data with the backup. Validates everything before changing anything. */
    suspend fun importBackup(json: String): BackupImportResult

    /** Every logged entry as CSV (one row per habit and day), for spreadsheets. */
    suspend fun exportCsv(): String
}

/** Reads and writes documents the user picked with the system file picker (no storage permission needed). */
interface DocumentStore {
    suspend fun read(uri: String): String

    suspend fun write(uri: String, text: String)
}
