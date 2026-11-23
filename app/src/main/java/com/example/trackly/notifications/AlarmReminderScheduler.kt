package com.example.trackly.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.trackly.domain.logic.ReminderTimeCalculator
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.domain.repository.ReminderScheduler
import com.example.trackly.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Registers one inexact, doze-friendly alarm per enabled reminder (no exact-alarm permission needed).
 * Each alarm fires [ReminderReceiver], which posts the notification and registers the next occurrence.
 */
class AlarmReminderScheduler(
    context: Context,
    private val habits: HabitRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ReminderScheduler {

    private val context = context.applicationContext
    private val alarmManager: AlarmManager? = this.context.getSystemService(AlarmManager::class.java)
    private val registry = this.context.getSharedPreferences(REGISTRY_FILE, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun syncAll() = sync(from = LocalDateTime.now(clock))

    /**
     * Called when reminder [reminderId] fired: its next occurrence is searched after [firedAt] (the time it was
     * scheduled for). Other reminders keep a due-but-not-yet-delivered alarm (see [sync]).
     */
    suspend fun syncAfterFiring(reminderId: Long, firedAt: LocalDateTime) {
        registry.edit().remove(pendingKey(reminderId)).apply()
        val now = LocalDateTime.now(clock)
        sync(from = if (firedAt.isAfter(now)) firedAt else now)
    }

    private suspend fun sync(from: LocalDateTime) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val manager = alarmManager ?: return@withContext
            val enabled = settings.current().notificationsEnabled
            val desired = HashMap<Long, Long>()
            val nowMillis = clock.millis()
            if (enabled) {
                for (habit in habits.getHabits()) {
                    for (reminder in habit.reminders) {
                        // Inexact alarms can be delivered late (Doze). An occurrence that is already due but has
                        // not fired yet must not be skipped by a re-sync: keep it while it is still valid.
                        val pending = registry.getLong(pendingKey(reminder.id), 0L)
                        val keepPending = pending in (nowMillis - LATE_DELIVERY_GRACE_MILLIS)..nowMillis &&
                            ReminderTimeCalculator.nextTrigger(habit, reminder, toLocal(pending).minusSeconds(1)) ==
                            toLocal(pending)
                        if (keepPending) {
                            desired[reminder.id] = pending
                            continue
                        }
                        val next = ReminderTimeCalculator.nextTrigger(habit, reminder, from) ?: continue
                        desired[reminder.id] = next.atZone(zone()).toInstant().toEpochMilli()
                    }
                }
            }
            val previous = registry.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
            for (staleId in previous) if (staleId !in desired) cancel(manager, staleId)
            for ((id, at) in desired) schedule(manager, id, at)
            val editor = registry.edit().clear().putStringSet(KEY_IDS, desired.keys.map { it.toString() }.toSet())
            for ((id, at) in desired) editor.putLong(pendingKey(id), at)
            editor.apply()
        }
    }

    private fun zone(): ZoneId = clock.zone

    private fun toLocal(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone()).withNano(0)

    private fun pendingKey(reminderId: Long) = "at_$reminderId"

    private fun schedule(manager: AlarmManager, reminderId: Long, triggerAtMillis: Long) {
        val pending = pendingIntent(reminderId, triggerAtMillis, create = true) ?: return
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } catch (error: SecurityException) {
            Log.w(TAG, "Could not schedule reminder $reminderId", error)
        }
    }

    private fun cancel(manager: AlarmManager, reminderId: Long) {
        pendingIntent(reminderId, 0L, create = false)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(reminderId: Long, triggerAtMillis: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            data = Uri.parse("trackly://reminder/$reminderId")
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderReceiver.EXTRA_TRIGGER_AT, triggerAtMillis)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, (reminderId % Int.MAX_VALUE).toInt(), intent, flags)
    }

    private companion object {
        const val TAG = "ReminderScheduler"
        const val REGISTRY_FILE = "reminder_registry"
        const val KEY_IDS = "registered_reminder_ids"
        const val LATE_DELIVERY_GRACE_MILLIS = 3L * 60 * 60 * 1000
    }
}
