package com.example.trackly.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.trackly.core.util.toEnumOrDefault
import com.example.trackly.domain.model.AccentTheme
import com.example.trackly.domain.model.AppSettings
import com.example.trackly.domain.model.EntitlementStatus
import com.example.trackly.domain.model.FirstDayOfWeekSetting
import com.example.trackly.domain.model.PremiumEntitlement
import com.example.trackly.domain.model.ThemeMode
import com.example.trackly.domain.model.UnitSystem
import com.example.trackly.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "trackly_settings")

// Separate file so it can be excluded from backups (see res/xml/backup_rules.xml).
private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(name = "trackly_entitlement")

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val dataStore = context.applicationContext.settingsDataStore
    private val entitlementStore = context.applicationContext.entitlementDataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val UNITS = stringPreferencesKey("unit_system")
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val ENTITLEMENT_STATUS = stringPreferencesKey("entitlement_status")
        val ENTITLEMENT_PRODUCT = stringPreferencesKey("entitlement_product")
        val ENTITLEMENT_VERIFIED_AT = longPreferencesKey("entitlement_verified_at")
    }

    private fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { error ->
        // A corrupted or unreadable file must never crash the app: fall back to defaults.
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    private val safeData: Flow<Preferences> = dataStore.safeData()

    override val settings: Flow<AppSettings> = safeData.map { it.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setThemeMode(mode: ThemeMode) = write { it[Keys.THEME] = mode.name }
    override suspend fun setAccent(accent: AccentTheme) = write { it[Keys.ACCENT] = accent.name }
    override suspend fun setNotificationsEnabled(enabled: Boolean) = write { it[Keys.NOTIFICATIONS] = enabled }
    override suspend fun setSoundEnabled(enabled: Boolean) = write { it[Keys.SOUND] = enabled }
    override suspend fun setVibrationEnabled(enabled: Boolean) = write { it[Keys.VIBRATION] = enabled }
    override suspend fun setFirstDayOfWeek(value: FirstDayOfWeekSetting) = write { it[Keys.FIRST_DAY] = value.name }
    override suspend fun setUnitSystem(value: UnitSystem) = write { it[Keys.UNITS] = value.name }
    override suspend fun setOnboardingCompleted(completed: Boolean) = write { it[Keys.ONBOARDING] = completed }

    override suspend fun loadCachedEntitlement(): PremiumEntitlement {
        val prefs = entitlementStore.safeData().first()
        val status = prefs[Keys.ENTITLEMENT_STATUS].toEnumOrDefault(EntitlementStatus.NONE)
        return PremiumEntitlement(
            status = status,
            productId = prefs[Keys.ENTITLEMENT_PRODUCT],
            verifiedAtMillis = prefs[Keys.ENTITLEMENT_VERIFIED_AT],
        )
    }

    override suspend fun saveCachedEntitlement(entitlement: PremiumEntitlement) {
        write(entitlementStore) { prefs ->
            prefs[Keys.ENTITLEMENT_STATUS] = entitlement.status.name
            val product = entitlement.productId
            if (product != null) prefs[Keys.ENTITLEMENT_PRODUCT] = product else prefs.remove(Keys.ENTITLEMENT_PRODUCT)
            val verifiedAt = entitlement.verifiedAtMillis
            if (verifiedAt != null) {
                prefs[Keys.ENTITLEMENT_VERIFIED_AT] = verifiedAt
            } else {
                prefs.remove(Keys.ENTITLEMENT_VERIFIED_AT)
            }
        }
    }

    private suspend fun write(
        store: DataStore<Preferences> = dataStore,
        block: (MutablePreferences) -> Unit,
    ) {
        try {
            store.edit { block(it) }
        } catch (ignored: IOException) {
            // Disk full / unreadable: the in-memory change is lost but the app keeps working.
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = this[Keys.THEME].toEnumOrDefault(defaults.themeMode),
            accent = this[Keys.ACCENT].toEnumOrDefault(defaults.accent),
            notificationsEnabled = this[Keys.NOTIFICATIONS] ?: defaults.notificationsEnabled,
            soundEnabled = this[Keys.SOUND] ?: defaults.soundEnabled,
            vibrationEnabled = this[Keys.VIBRATION] ?: defaults.vibrationEnabled,
            firstDayOfWeek = this[Keys.FIRST_DAY].toEnumOrDefault(defaults.firstDayOfWeek),
            unitSystem = this[Keys.UNITS].toEnumOrDefault(defaults.unitSystem),
            onboardingCompleted = this[Keys.ONBOARDING] ?: defaults.onboardingCompleted,
        )
    }
}
