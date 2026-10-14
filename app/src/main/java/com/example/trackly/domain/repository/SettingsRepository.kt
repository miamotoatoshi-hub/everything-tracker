package com.example.trackly.domain.repository

import com.example.trackly.domain.model.AccentTheme
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.FirstDayOfWeekSetting
import com.example.trackly.domain.model.PremiumEntitlement
import com.example.trackly.domain.model.ThemeMode
import com.example.trackly.domain.model.UnitSystem
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAccent(accent: AccentTheme)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setFirstDayOfWeek(value: FirstDayOfWeekSetting)
    suspend fun setUnitSystem(value: UnitSystem)
    suspend fun setOnboardingCompleted(completed: Boolean)

    /** Last entitlement confirmed by Google Play, used only as an offline fallback. */
    suspend fun loadCachedEntitlement(): PremiumEntitlement
    suspend fun saveCachedEntitlement(entitlement: PremiumEntitlement)
}
