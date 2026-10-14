package com.example.trackly.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.trackly.TracklyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Re-registers reminder alarms after a reboot, app update, or clock / time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? TracklyApp ?: return
        val pending = goAsync()
        scope.launch {
            try {
                app.container.todayProvider.refresh()
                app.container.reminderScheduler.syncAll()
            } catch (error: Exception) {
                Log.e(TAG, "Could not restore reminders", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
