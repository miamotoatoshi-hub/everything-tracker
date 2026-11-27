package com.example.trackly.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.trackly.MainActivity
import com.example.trackly.R
import com.example.trackly.TracklyApp
import com.example.trackly.domain.model.Habit
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Premium "evening summary": one notification listing the habits still left today. */
object DailySummaryScheduler {
    private const val UNIQUE_NAME = "daily_summary"

    /** Milliseconds from [now] to the next [minutesOfDay] (today if still ahead, otherwise tomorrow). */
    fun delayMillis(now: LocalDateTime, minutesOfDay: Int): Long {
        var next = now.toLocalDate().atTime(minutesOfDay / 60, minutesOfDay % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    fun schedule(context: Context, minutesOfDay: Int) {
        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(delayMillis(LocalDateTime.now(), minutesOfDay), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    /** "Read, Walk, Yoga and 2 more" style list for the notification text. */
    fun habitList(habits: List<Habit>, max: Int = 4): String {
        val names = habits.take(max).joinToString(", ") { "${it.icon} ${it.name}".trim() }
        val extra = habits.size - max
        return if (extra > 0) "$names +$extra" else names
    }
}

class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TracklyApp ?: return Result.failure()
        val container = app.container
        return try {
            val settings = container.settingsRepository.current()
            if (!settings.dailySummaryEnabled) return Result.success()
            val today = container.todayProvider.current()
            val due = container.habitRepository.getHabits().filter { !it.isArchived && it.isActiveOn(today) }
            val left = due.filter { container.habitRepository.getEntry(it.id, today)?.completed != true }
            if (settings.notificationsEnabled && left.isNotEmpty()) {
                notify(left, due.size, settings.soundEnabled, settings.vibrationEnabled)
            }
            DailySummaryScheduler.schedule(applicationContext, settings.dailySummaryMinutes)
            Result.success()
        } catch (error: Exception) {
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission") // Checked just below.
    private fun notify(left: List<Habit>, dueCount: Int, sound: Boolean, vibration: Boolean) {
        val context = applicationContext
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val channel = NotificationChannels.ensure(context, sound, vibration)
        val title = context.resources.getQuantityString(R.plurals.summary_title, left.size, left.size, dueCount)
        val text = context.getString(R.string.summary_text, DailySummaryScheduler.habitList(left))
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_trackly)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (ignored: SecurityException) {
            // Permission revoked in the meantime.
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 7_000_001
    }
}
