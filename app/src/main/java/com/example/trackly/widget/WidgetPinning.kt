package com.example.trackly.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Asks the launcher to place the "Today" widget. Returns false when the launcher cannot pin widgets. */
fun requestPinTodayWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    if (!manager.isRequestPinAppWidgetSupported) return false
    return try {
        manager.requestPinAppWidget(ComponentName(context, TodayWidgetReceiver::class.java), null, null)
    } catch (error: IllegalStateException) {
        false
    }
}
