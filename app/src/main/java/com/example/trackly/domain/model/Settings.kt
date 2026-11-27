package com.example.trackly.domain.model

import java.time.DayOfWeek
import java.util.Locale
import java.time.temporal.WeekFields

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-wide accent theme. Premium accents are unlocked by the subscription. */
enum class AccentTheme(val requiresPremium: Boolean) {
    INDIGO(false),
    TEAL(false),
    SUNSET(true),
    ROSE(true),
    FOREST(true),
    GRAPE(true),
    OCEAN(true),
    GRAPHITE(true),
}

enum class FirstDayOfWeekSetting(private val fixed: DayOfWeek?) {
    SYSTEM(null),
    MONDAY(DayOfWeek.MONDAY),
    SATURDAY(DayOfWeek.SATURDAY),
    SUNDAY(DayOfWeek.SUNDAY),
    ;

    fun resolve(locale: Locale = Locale.getDefault()): DayOfWeek =
        fixed ?: WeekFields.of(locale).firstDayOfWeek
}

enum class UnitSystem { METRIC, IMPERIAL }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentTheme = AccentTheme.INDIGO,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val firstDayOfWeek: FirstDayOfWeekSetting = FirstDayOfWeekSetting.SYSTEM,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val onboardingCompleted: Boolean = false,
    /** Premium: true black backgrounds in the dark theme (AMOLED). */
    val pureBlack: Boolean = false,
    /** Premium: salted hash of the app-lock PIN (see PinHasher); null = no lock. */
    val appLockPinHash: String? = null,
    /** Premium: unlock with fingerprint / face as well as the PIN. */
    val appLockBiometric: Boolean = false,
    /** Premium: an evening notification listing the habits still left today. */
    val dailySummaryEnabled: Boolean = false,
    /** Minutes after midnight for the evening summary. */
    val dailySummaryMinutes: Int = 21 * 60,
) {
    val appLockEnabled: Boolean get() = appLockPinHash != null
}
