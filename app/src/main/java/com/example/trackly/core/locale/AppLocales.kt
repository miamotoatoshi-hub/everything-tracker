package com.example.trackly.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Per-app language. To offer another language: add `values-<code>/strings*.xml`, list the code in
 * [supportedTags] and in `res/xml/locales_config.xml`.
 */
object AppLocales {
    val supportedTags: List<String> = listOf("en", "ru", "es", "de", "fr", "pt-BR")

    /** The explicitly chosen language tag, or null when following the system language. */
    fun currentTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        val locale = (if (locales.isEmpty) null else locales[0]) ?: return null
        // "pt-BR" is offered with its region; every other language by its plain code.
        return supportedTags.firstOrNull { it.equals(locale.toLanguageTag(), ignoreCase = true) }
            ?: supportedTags.firstOrNull { it == locale.language }
            ?: locale.toLanguageTag()
    }

    fun apply(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
