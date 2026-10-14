package com.example.trackly.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.trackly.R
import com.example.trackly.domain.model.PremiumFeature

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val REORDER = "reorder"
    const val WEEKLY = "weekly"
    const val CREATE = "create"

    const val ARG_ID = "id"
    const val ARG_FEATURE = "feature"
    const val ARG_KIND = "kind"

    const val HABIT = "habit/{$ARG_ID}"
    const val EDIT = "edit/{$ARG_ID}"
    const val HISTORY = "history/{$ARG_ID}"
    const val PREMIUM = "premium?$ARG_FEATURE={$ARG_FEATURE}"
    const val LEGAL = "legal/{$ARG_KIND}"

    fun habit(id: Long) = "habit/$id"
    fun edit(id: Long) = "edit/$id"
    fun history(id: Long) = "history/$id"
    fun premium(feature: PremiumFeature? = null) =
        if (feature == null) "premium" else "premium?$ARG_FEATURE=${feature.name}"
    fun legal(kind: String) = "legal/$kind"
}

/** Destinations shown in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    CALENDAR(Routes.CALENDAR, R.string.nav_calendar, Icons.Rounded.CalendarMonth),
    STATS(Routes.STATS, R.string.nav_statistics, Icons.Rounded.BarChart),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
    ;

    companion object {
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}
