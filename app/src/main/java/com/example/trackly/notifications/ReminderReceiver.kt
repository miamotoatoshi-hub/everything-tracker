package com.example.trackly.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.trackly.TracklyApp
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.Reminder
import com.example.trackly.domain.model.ReminderRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Handles reminder alarms and the notification's quick-log action. Works while the app is closed. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? TracklyApp ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> handleFire(app, intent)
                    ACTION_QUICK_LOG -> handleQuickLog(app, intent)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Reminder handling failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleFire(app: TracklyApp, intent: Intent) {
        val container = app.container
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val scheduledMillis = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L)
        val clock = container.clock
        val firedAt = if (scheduledMillis > 0) {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledMillis), clock.zone)
        } else {
            LocalDateTime.now(clock)
        }

        val habits = container.habitRepository.getHabits()
        val habit = habits.firstOrNull { h -> h.reminders.any { it.id == reminderId } }
        val reminder = habit?.reminders?.firstOrNull { it.id == reminderId }

        if (habit != null && reminder != null) {
            val settings = container.settingsRepository.current()
            // The day the reminder was meant for (a late delivery just after midnight still belongs to it).
            val day = firedAt.toLocalDate()
            val stale = day.isBefore(LocalDate.now(clock).minusDays(1))
            val done = container.habitRepository.getEntry(habit.id, day)?.completed == true
            if (settings.notificationsEnabled && !stale && isDueToday(habit, reminder, day) && !done) {
                container.reminderNotifier.show(habit, reminder, settings, day)
            }
        }

        container.reminderScheduler.syncAfterFiring(reminderId, firedAt)
    }

    private suspend fun handleQuickLog(app: TracklyApp, intent: Intent) {
        val container = app.container
        val habitId = intent.getLongExtra(ReminderNotifier.EXTRA_HABIT_ID, -1L)
        val habit = container.habitRepository.getHabit(habitId) ?: return
        val today = LocalDate.now(container.clock)
        // Log the day the notification was for, unless it is older than yesterday.
        val day = intent.getLongExtra(ReminderNotifier.EXTRA_EPOCH_DAY, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let { LocalDate.ofEpochDay(it) }
            ?.takeIf { !it.isAfter(today) && !it.isBefore(today.minusDays(1)) }
            ?: today
        val repository = container.habitRepository
        if (habit.type == HabitType.BOOLEAN) {
            // "Done" must never undo: only set the day as done.
            if (repository.getEntry(habit.id, day)?.completed != true) repository.setEntryValue(habit.id, day, 1.0)
        } else {
            repository.addToEntry(habit.id, day, habit.stepValue)
        }
        container.reminderNotifier.cancel(habit.id)
    }

    private fun isDueToday(habit: Habit, reminder: Reminder, today: LocalDate): Boolean = when (reminder.repeat) {
        ReminderRepeat.EVERY_DAY -> !habit.isArchived && !today.isBefore(habit.startDate) && !habit.isPausedOn(today)
        ReminderRepeat.HABIT_SCHEDULE -> !habit.isArchived && habit.isActiveOn(today)
    }

    companion object {
        const val ACTION_FIRE = "com.example.trackly.action.FIRE_REMINDER"
        const val ACTION_QUICK_LOG = "com.example.trackly.action.QUICK_LOG"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        private const val TAG = "ReminderReceiver"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
