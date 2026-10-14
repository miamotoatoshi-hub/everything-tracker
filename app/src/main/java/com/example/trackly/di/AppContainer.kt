package com.example.trackly.di

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.trackly.billing.BillingConfig
import com.example.trackly.billing.BillingRepository
import com.example.trackly.billing.PlayBillingRepository
import com.example.trackly.core.time.SystemClock
import com.example.trackly.core.time.TodayProvider
import com.example.trackly.data.local.TracklyDatabase
import com.example.trackly.data.repository.HabitRepositoryImpl
import com.example.trackly.data.repository.SettingsRepositoryImpl
import com.example.trackly.domain.model.PremiumPolicy
import com.example.trackly.domain.repository.HabitRepository
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.domain.usecase.SaveHabitUseCase
import com.example.trackly.notifications.AlarmReminderScheduler
import com.example.trackly.notifications.NotificationChannels
import com.example.trackly.notifications.ReminderNotifier
import com.example.trackly.notifications.ReminderSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Debug-only Premium override. Nothing in release builds ever writes to it
 * (the only writer lives in the `debug` source set), so release Premium always comes from Google Play.
 */
class PremiumOverride {
    val value = MutableStateFlow<Boolean?>(null)
}

/** Hand-rolled dependency container: simple, explicit, and easy to replace in tests. */
class AppContainer(
    private val app: Application,
    val appScope: CoroutineScope,
) {
    val clock: Clock = SystemClock

    val todayProvider = TodayProvider(clock)

    val database: TracklyDatabase by lazy { TracklyDatabase.create(app) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(app) }

    val habitRepository: HabitRepository by lazy {
        HabitRepositoryImpl(database, clock) { reminderScheduler.syncAll() }
    }

    val reminderScheduler: AlarmReminderScheduler by lazy {
        AlarmReminderScheduler(app, habitRepository, settingsRepository, clock)
    }

    val reminderNotifier: ReminderNotifier by lazy { ReminderNotifier(app) }

    val billingRepository: BillingRepository by lazy {
        PlayBillingRepository(app, BillingConfig.PREMIUM_PRODUCT_ID, settingsRepository, clock, appScope)
    }

    val premiumOverride = PremiumOverride()

    /** The effective Premium state: Google Play's answer (plus the debug-only override in debug builds). */
    val isPremium: StateFlow<Boolean> by lazy {
        combine(billingRepository.isPremium, premiumOverride.value) { real, override -> override ?: real }
            .stateIn(appScope, SharingStarted.Eagerly, billingRepository.isPremium.value)
    }

    val premiumPolicy: StateFlow<PremiumPolicy> by lazy {
        isPremium.map { PremiumPolicy(it) }
            .stateIn(appScope, SharingStarted.Eagerly, PremiumPolicy(isPremium.value))
    }

    val saveHabit: SaveHabitUseCase by lazy { SaveHabitUseCase(habitRepository) }

    /** Called once from [com.example.trackly.TracklyApp.onCreate]. */
    fun start() {
        registerTimeChangeReceiver()
        ReminderSyncWorker.schedule(app)
        appScope.launch {
            try {
                // Channels are created on demand; syncing re-arms alarms lost to updates or force-stops.
                reminderScheduler.syncAll()
            } catch (ignored: Exception) {
                // Reminders are best-effort; never crash start-up.
            }
        }
    }

    private fun registerTimeChangeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                todayProvider.refresh()
                if (intent.action != Intent.ACTION_DATE_CHANGED) {
                    appScope.launch {
                        try {
                            reminderScheduler.syncAll()
                        } catch (ignored: Exception) {
                            // best effort
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
