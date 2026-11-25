package com.example.trackly.presentation

import com.example.trackly.presentation.legal.LegalBlock
import com.example.trackly.presentation.legal.LegalText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LegalTextTest {
    @Test
    fun `headings and paragraphs are split`() {
        val blocks = LegalText.parse("Intro line.\n\n# Summary\n• One\n• Two\n\n# Contact\nWrite to us.")
        assertEquals(
            listOf(
                LegalBlock.Paragraph("Intro line."),
                LegalBlock.Heading("Summary"),
                LegalBlock.Paragraph("• One\n• Two"),
                LegalBlock.Heading("Contact"),
                LegalBlock.Paragraph("Write to us."),
            ),
            blocks,
        )
    }

    @Test
    fun `effective date is localised and bad input is shown as written`() {
        assertEquals("November 25, 2026", LegalText.effectiveDate("2026-11-25", Locale.US))
        assertEquals("soon", LegalText.effectiveDate("soon", Locale.US))
    }

    @Test
    fun `web and e-mail addresses become links`() {
        val text = "See https://policies.google.com/privacy. Mail help@example.com, thanks"
        val links = LegalText.links(text)
        assertEquals(listOf("https://policies.google.com/privacy", "mailto:help@example.com"), links.map { it.third })
        assertEquals("https://policies.google.com/privacy", text.substring(links[0].first.first, links[0].first.last + 1))
    }
}
