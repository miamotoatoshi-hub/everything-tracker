package com.example.trackly.core.locale

import android.content.res.Resources
import java.util.Locale

/**
 * The locale whose rules decide the first day of the week. A per-app language such as plain "en" carries no
 * region, so the phone's own region (UK, Spain, …) is used then.
 */
fun weekLocale(): Locale {
    val app = Locale.getDefault()
    if (app.country.isNotEmpty()) return app
    val system = try {
        Resources.getSystem()?.configuration?.locales?.get(0)
    } catch (error: RuntimeException) {
        null
    }
    return system?.takeIf { it.country.isNotEmpty() }?.let { Locale.Builder().setLocale(it).build() } ?: app
}
