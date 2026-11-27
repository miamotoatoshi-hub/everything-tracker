package com.example.trackly.core.shortcuts

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.trackly.MainActivity
import com.example.trackly.R

/** Long-press on the app icon: New habit, Statistics, Achievements. */
object AppShortcuts {
    const val ACTION_NEW_HABIT = "com.example.trackly.action.NEW_HABIT"
    const val ACTION_OPEN_STATS = "com.example.trackly.action.OPEN_STATS"
    const val ACTION_OPEN_ACHIEVEMENTS = "com.example.trackly.action.OPEN_ACHIEVEMENTS"

    /** Re-published on every launch, so the labels follow the app language. */
    fun install(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "new_habit", R.string.form_title_new, R.drawable.ic_shortcut_add, ACTION_NEW_HABIT),
            shortcut(context, "stats", R.string.nav_statistics, R.drawable.ic_shortcut_stats, ACTION_OPEN_STATS),
            shortcut(context, "achievements", R.string.achievements_title, R.drawable.ic_shortcut_trophy, ACTION_OPEN_ACHIEVEMENTS),
        )
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (error: RuntimeException) {
            // A launcher without shortcuts, or the rate limit: shortcuts are a convenience only.
        }
    }

    private fun shortcut(context: Context, id: String, @StringRes label: Int, @DrawableRes icon: Int, action: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            .build()
}
