package com.example.trackly.fakes

import com.example.trackly.domain.model.AccentTheme
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.FirstDayOfWeekSetting
import com.example.trackly.domain.model.PremiumEntitlement
import com.example.trackly.domain.model.ThemeMode
import com.example.trackly.domain.model.UnitSystem
import com.example.trackly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    var cachedEntitlement: PremiumEntitlement = PremiumEntitlement.None

    override val settings: Flow<AppSettings> = state
    override suspend fun current(): AppSettings = state.value

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setAccent(accent: AccentTheme) = state.update { it.copy(accent = accent) }
    override suspend fun setNotificationsEnabled(enabled: Boolean) = state.update { it.copy(notificationsEnabled = enabled) }
    override suspend fun setSoundEnabled(enabled: Boolean) = state.update { it.copy(soundEnabled = enabled) }
    override suspend fun setVibrationEnabled(enabled: Boolean) = state.update { it.copy(vibrationEnabled = enabled) }
    override suspend fun setFirstDayOfWeek(value: FirstDayOfWeekSetting) = state.update { it.copy(firstDayOfWeek = value) }
    override suspend fun setUnitSystem(value: UnitSystem) = state.update { it.copy(unitSystem = value) }
    override suspend fun setOnboardingCompleted(completed: Boolean) = state.update { it.copy(onboardingCompleted = completed) }
    override suspend fun loadCachedEntitlement(): PremiumEntitlement = cachedEntitlement
    override suspend fun saveCachedEntitlement(entitlement: PremiumEntitlement) {
        cachedEntitlement = entitlement
    }
}
