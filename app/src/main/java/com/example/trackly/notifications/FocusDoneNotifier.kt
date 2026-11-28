package com.example.trackly.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.trackly.MainActivity
import com.example.trackly.R
import com.example.trackly.domain.repository.SettingsRepository

/** "Focus session complete" — only when the timer ends while Trackly is not on screen. */
class FocusDoneNotifier(private val context: Context, private val settings: SettingsRepository) {

    @SuppressLint("MissingPermission") // Checked in canNotify().
    suspend fun notifyIfInBackground(habitLabel: String, minutes: Int) {
        if (isInForeground() || !canNotify()) return
        val appSettings = settings.current()
        if (!appSettings.notificationsEnabled) return
        val channel = NotificationChannels.ensure(context, appSettings.soundEnabled, appSettings.vibrationEnabled)
        val text = if (minutes > 0) {
            "$habitLabel · " + context.resources.getQuantityString(R.plurals.focus_logged, minutes, minutes)
        } else {
            habitLabel
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_trackly)
            .setContentTitle(context.getString(R.string.focus_done_title))
            .setContentText(text)
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

    private fun isInForeground(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val NOTIFICATION_ID = 7_000_002
    }
}
