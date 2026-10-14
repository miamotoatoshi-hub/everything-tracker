package com.example.trackly.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackly.billing.BillingEvent
import com.example.trackly.billing.BillingRepository
import com.example.trackly.domain.model.AccentTheme
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.FirstDayOfWeekSetting
import com.example.trackly.domain.model.PremiumEntitlement
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.domain.model.ThemeMode
import com.example.trackly.domain.model.UnitSystem
import com.example.trackly.domain.repository.ReminderScheduler
import com.example.trackly.domain.repository.SettingsRepository
import com.example.trackly.presentation.common.RetryableState
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.launchSafely
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine

data class SettingsData(
    val settings: AppSettings,
    val entitlement: PremiumEntitlement,
    val isPremium: Boolean,
)

sealed interface SettingsEvent {
    data class RequiresPremium(val feature: PremiumFeature) : SettingsEvent
    data object Failed : SettingsEvent
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val billing: BillingRepository,
    isPremium: StateFlow<Boolean>,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    private val source = RetryableState(viewModelScope) {
        combine(settings.settings, billing.entitlement, isPremium) { s, e, p -> SettingsData(s, e, p) }
    }

    val uiState: StateFlow<UiState<SettingsData>> = source.state

    /** Results of restore / purchase actions, to show as messages. */
    val billingEvents: SharedFlow<BillingEvent> = billing.events

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun retry() = source.retry()

    fun setTheme(mode: ThemeMode) = save { settings.setThemeMode(mode) }

    fun setAccent(accent: AccentTheme, isPremium: Boolean) {
        if (accent.requiresPremium && !isPremium) {
            _events.tryEmit(SettingsEvent.RequiresPremium(PremiumFeature.PREMIUM_THEMES))
        } else {
            save { settings.setAccent(accent) }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = save {
        settings.setNotificationsEnabled(enabled)
        reminders.syncAll()
    }

    fun setSound(enabled: Boolean) = save { settings.setSoundEnabled(enabled) }

    fun setVibration(enabled: Boolean) = save { settings.setVibrationEnabled(enabled) }

    fun setFirstDay(value: FirstDayOfWeekSetting) = save { settings.setFirstDayOfWeek(value) }

    fun setUnits(value: UnitSystem) = save { settings.setUnitSystem(value) }

    fun restorePurchases() = billing.restorePurchases()

    fun refreshBilling() {
        billing.connect()
    }

    private fun save(block: suspend () -> Unit) {
        launchSafely(onError = { _events.emit(SettingsEvent.Failed) }) { block() }
    }
}
