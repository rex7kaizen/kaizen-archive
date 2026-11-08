package com.kaizen.archive.importer

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppTxtParserTest {
    @Test
    fun parsesTurkishWhatsAppLinesAndMultilineMessages() {
        val input = """
            07.11.2026, 14:32 - Ahmet: Selam
            07.11.2026, 14:33 - Ayşe: Merhaba
            devam satırı
        """.trimIndent()

        val parsed = WhatsAppTxtParser().parse(BufferedReader(StringReader(input)))

        assertEquals(2, parsed.size)
        assertEquals("Ahmet", parsed[0].sender)
        assertEquals("Selam", parsed[0].text)
        assertEquals("Merhaba\ndevam satırı", parsed[1].text)
        assertTrue(parsed[1].timestamp > parsed[0].timestamp)
    }
}
