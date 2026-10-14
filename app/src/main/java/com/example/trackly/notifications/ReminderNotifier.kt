package com.example.trackly.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.trackly.MainActivity
import com.example.trackly.R
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.Reminder

/** Builds and posts habit reminder notifications. */
class ReminderNotifier(context: Context) {
    private val context = context.applicationContext

    /** True when the app is currently allowed to post notifications. */
    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // Guarded by canNotify().
    fun show(habit: Habit, reminder: Reminder, settings: AppSettings) {
        if (!canNotify()) return
        val channelId = NotificationChannels.ensure(context, settings.soundEnabled, settings.vibrationEnabled)
        val message = reminder.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_default_message, habit.name, habit.icon)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_trackly)
            .setContentTitle("${habit.icon} ${habit.name}")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(habit.colorArgb)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openHabitIntent(habit.id))

        val actionLabel = if (habit.type == HabitType.BOOLEAN) {
            context.getString(R.string.notification_action_done)
        } else {
            context.getString(R.string.notification_action_add)
        }
        builder.addAction(0, actionLabel, quickLogIntent(habit.id))

        try {
            NotificationManagerCompat.from(context).notify(notificationId(habit.id), builder.build())
        } catch (ignored: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(habitId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(habitId))
    }

    private fun openHabitIntent(habitId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_HABIT
            data = Uri.parse("trackly://habit/$habitId")
            putExtra(EXTRA_HABIT_ID, habitId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId(habitId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun quickLogIntent(habitId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_QUICK_LOG
            data = Uri.parse("trackly://quicklog/$habitId")
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId(habitId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_OPEN_HABIT = "com.example.trackly.action.OPEN_HABIT"
        const val EXTRA_HABIT_ID = "habit_id"

        fun notificationId(habitId: Long): Int = (habitId % Int.MAX_VALUE).toInt()
    }
}
