package com.example.trackly.presentation.common

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.trackly.R
import com.example.trackly.domain.model.Habit
import com.example.trackly.domain.model.HabitType
import com.example.trackly.domain.model.HabitUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Whole numbers print without decimals; others with up to two. */
fun formatValue(value: Double, locale: Locale = Locale.getDefault()): String {
    val format = DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(locale))
    format.isGroupingUsed = false
    return format.format(value)
}

/** Parses a number typed by the user, accepting both "," and "." as decimal separator. */
fun parseNumber(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

@StringRes
fun HabitUnit.labelRes(): Int = when (this) {
    HabitUnit.TIMES -> R.string.unit_times
    HabitUnit.MINUTES -> R.string.unit_minutes
    HabitUnit.HOURS -> R.string.unit_hours
    HabitUnit.STEPS -> R.string.unit_steps
    HabitUnit.KM -> R.string.unit_km
    HabitUnit.MILES -> R.string.unit_miles
    HabitUnit.METERS -> R.string.unit_meters
    HabitUnit.GLASSES -> R.string.unit_glasses
    HabitUnit.PAGES -> R.string.unit_pages
    HabitUnit.CUSTOM -> R.string.unit_custom
}

@StringRes
fun HabitType.labelRes(): Int = when (this) {
    HabitType.BOOLEAN -> R.string.type_boolean
    HabitType.COUNTER -> R.string.type_counter
    HabitType.DURATION -> R.string.type_duration
    HabitType.DISTANCE -> R.string.type_distance
    HabitType.QUANTITY -> R.string.type_quantity
}

@StringRes
fun HabitType.descriptionRes(): Int = when (this) {
    HabitType.BOOLEAN -> R.string.type_boolean_desc
    HabitType.COUNTER -> R.string.type_counter_desc
    HabitType.DURATION -> R.string.type_duration_desc
    HabitType.DISTANCE -> R.string.type_distance_desc
    HabitType.QUANTITY -> R.string.type_quantity_desc
}

/** The unit text for a habit, honouring the user's custom unit. */
@Composable
fun unitLabel(unit: HabitUnit, customUnit: String?): String =
    if (unit == HabitUnit.CUSTOM && !customUnit.isNullOrBlank()) customUnit else stringResource(unit.labelRes())

@Composable
fun unitLabel(habit: Habit): String = unitLabel(habit.unit, habit.customUnit)

/** "5 / 8 glasses", or "Done" / "Not done" for yes/no habits. */
@Composable
fun progressText(habit: Habit, value: Double): String {
    val locale = rememberLocale()
    return if (habit.type == HabitType.BOOLEAN) {
        stringResource(if (value >= habit.goalValue) R.string.status_done else R.string.status_not_done)
    } else {
        stringResource(
            R.string.progress_format,
            formatValue(value, locale),
            formatValue(habit.goalValue, locale),
            unitLabel(habit),
        )
    }
}

@Composable
fun rememberLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
}

/** Locale-aware date/time formatting that respects the device's 12/24-hour setting. */
class DateFormats(private val locale: Locale, private val is24Hour: Boolean) {
    private fun formatter(skeleton: String, fallback: () -> DateTimeFormatter): DateTimeFormatter =
        try {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        } catch (error: IllegalArgumentException) {
            // A pattern letter java.time does not know: fall back to the locale's standard format.
            fallback()
        }

    private fun dateFallback() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun fullDate(date: LocalDate): String = formatter("EEEEMMMMd") {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }.format(date)

    fun mediumDate(date: LocalDate): String = formatter("MMMd") { dateFallback() }.format(date)
    fun dayAndMonth(date: LocalDate): String = formatter("EEEMMMd") { dateFallback() }.format(date)
    fun monthYear(month: YearMonth): String = try {
        formatter("MMMMy") { dateFallback() }.format(month)
    } catch (error: java.time.temporal.UnsupportedTemporalTypeException) {
        month.toString()
    }

    fun month(month: YearMonth): String = try {
        formatter("MMM") { dateFallback() }.format(month)
    } catch (error: java.time.temporal.UnsupportedTemporalTypeException) {
        month.toString()
    }

    fun year(date: LocalDate): String = date.year.toString()
    fun time(time: LocalTime): String = formatter(if (is24Hour) "Hm" else "hm") {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }.format(time)

    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)
    fun weekdayNarrow(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)
    fun weekdayFull(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale)
}

@Composable
fun rememberDateFormats(): DateFormats {
    val locale = rememberLocale()
    val context = LocalContext.current
    return remember(locale) { DateFormats(locale, DateFormat.is24HourFormat(context)) }
}
