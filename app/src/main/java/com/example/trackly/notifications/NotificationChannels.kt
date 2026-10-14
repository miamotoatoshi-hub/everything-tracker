package com.example.trackly.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.example.trackly.R

/**
 * On Android 8+ sound and vibration belong to the notification *channel* and cannot change after the
 * channel is created. The in-app sound/vibration switches therefore pick one of four channels.
 */
object NotificationChannels {
    private const val PREFIX = "reminders"
    private val VIBRATION_PATTERN = longArrayOf(0, 250, 150, 250)

    fun channelId(sound: Boolean, vibration: Boolean): String = when {
        sound && vibration -> "${PREFIX}_sound_vibration"
        sound -> "${PREFIX}_sound"
        vibration -> "${PREFIX}_vibration"
        else -> "${PREFIX}_silent"
    }

    /** Creates the channel for the given preferences if needed and returns its id. */
    fun ensure(context: Context, sound: Boolean, vibration: Boolean): String {
        val id = channelId(sound, vibration)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return id
        if (manager.getNotificationChannel(id) != null) return id

        val nameRes = when {
            sound && vibration -> R.string.channel_reminders_sound_vibration
            sound -> R.string.channel_reminders_sound
            vibration -> R.string.channel_reminders_vibration
            else -> R.string.channel_reminders_silent
        }
        val importance = if (sound || vibration) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(id, context.getString(nameRes), importance).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(vibration)
            if (vibration) vibrationPattern = VIBRATION_PATTERN
            if (sound) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attributes)
            } else {
                setSound(null, null)
            }
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        return id
    }
}
