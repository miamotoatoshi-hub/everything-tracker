package com.example.trackly.domain.repository

/** Keeps system alarms in sync with the reminders stored in the database. */
interface ReminderScheduler {
    /** Cancels stale alarms and (re)registers the next alarm of every enabled reminder. */
    suspend fun syncAll()
}
