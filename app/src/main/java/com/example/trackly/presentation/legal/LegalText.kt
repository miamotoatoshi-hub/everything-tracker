package com.example.trackly.presentation.legal

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

sealed interface LegalBlock {
    data class Heading(val text: String) : LegalBlock
    data class Paragraph(val text: String) : LegalBlock
}

/** Pure helpers for the bundled Privacy Policy / Terms (string resources with "# " headings). */
object LegalText {

    /** Splits a legal text into headings ("# Title" lines) and paragraphs (separated by blank lines). */
    fun parse(text: String): List<LegalBlock> =
        text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }.flatMap { chunk ->
            if (chunk.startsWith("# ")) {
                val lines = chunk.lines()
                val rest = lines.drop(1).joinToString("\n").trim()
                listOf(LegalBlock.Heading(lines.first().removePrefix("# ").trim())) +
                    (if (rest.isNotEmpty()) listOf(LegalBlock.Paragraph(rest)) else emptyList())
            } else {
                listOf(LegalBlock.Paragraph(chunk))
            }
        }

    /** "2026-11-25" → "November 25, 2026" in [locale]; anything unparsable is shown as written. */
    fun effectiveDate(iso: String, locale: Locale): String = try {
        LocalDate.parse(iso.trim()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    } catch (error: DateTimeParseException) {
        iso
    }

    private val LINK = Regex("""https?://[^\s)]+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    /** Web addresses and e-mail addresses in [text], with the URI each one should open. */
    fun links(text: String): List<Triple<IntRange, String, String>> =
        LINK.findAll(text).map { match ->
            val value = match.value.trimEnd('.', ',')
            val uri = if (value.contains("://")) value else "mailto:$value"
            Triple(match.range.first until match.range.first + value.length, value, uri)
        }.toList()
}
