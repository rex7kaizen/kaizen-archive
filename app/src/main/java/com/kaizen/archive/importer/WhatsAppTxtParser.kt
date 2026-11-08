package com.kaizen.archive.importer

import java.io.BufferedReader
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Parses common WhatsApp plain-text export formats. It never accesses WhatsApp private storage. */
class WhatsAppTxtParser {
    private val linePattern = Regex(
        """^\u200E?\[?(\d{1,2}[./-]\d{1,2}[./-]\d{2,4},?\s+\d{1,2}:\d{2}(?:\s?[APap][Mm])?)\]?\s+-\s+([^:]+):\s?(.*)$""",
    )

    private val formats = listOf(
        "d.M.yyyy, HH:mm", "dd.MM.yyyy, HH:mm", "d.M.yy, HH:mm", "dd.MM.yy, HH:mm",
        "d.M.yyyy HH:mm", "dd.MM.yyyy HH:mm", "d/M/yyyy, HH:mm", "dd/MM/yyyy, HH:mm",
        "M/d/yy, h:mm a", "MM/dd/yy, h:mm a", "M/d/yyyy, h:mm a", "MM/dd/yyyy, h:mm a",
        "d-M-yyyy, HH:mm", "dd-MM-yyyy, HH:mm",
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.forLanguageTag("tr-TR")).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
    }

    fun parse(reader: BufferedReader): List<ParsedExportMessage> {
        val output = mutableListOf<ParsedExportMessage>()
        var current: ParsedExportMessage? = null

        fun flush() {
            current?.let(output::add)
            current = null
        }

        reader.forEachLine { rawLine ->
            val line = rawLine.removePrefix("‎").removePrefix("‏")
            val match = linePattern.matchEntire(line)
            if (match != null) {
                val timestamp = parseTimestamp(match.groupValues[1])
                if (timestamp != null) {
                    flush()
                    current = ParsedExportMessage(
                        sender = match.groupValues[2].trim().ifBlank { "Bilinmeyen" },
                        text = match.groupValues[3],
                        timestamp = timestamp,
                    )
                } else if (current != null) {
                    current = current!!.copy(text = current!!.text + "\n" + line)
                }
            } else if (current != null) {
                current = current!!.copy(text = current!!.text + "\n" + line)
            }
        }
        flush()
        return output.filter { it.text.isNotBlank() }
    }

    private fun parseTimestamp(value: String): Long? {
        for (format in formats) {
            val position = ParsePosition(0)
            val parsed: Date? = format.parse(value.trim(), position)
            if (parsed != null && position.index == value.trim().length) return parsed.time
        }
        return null
    }
}

data class ParsedExportMessage(
    val sender: String,
    val text: String,
    val timestamp: Long,
)
