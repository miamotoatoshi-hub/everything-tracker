package com.example.trackly.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.trackly.TracklyApp
import java.util.concurrent.TimeUnit

/**
 * Safety net: some devices drop alarms (aggressive battery managers, backups restored, ...).
 * This periodic job simply re-registers them.
 */
class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TracklyApp ?: return Result.failure()
        return try {
            app.container.reminderScheduler.syncAll()
            Result.success()
        } catch (error: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "reminder_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderSyncWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
