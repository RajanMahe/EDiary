package com.aburv.kurippidu.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

data class ParsedDiary(
    val date: LocalDate,
    val content: String
)

/**
 * Splits plain text into diary entries. A line that is only a date starts a
 * new entry; the lines below it (until the next date line) are its content.
 */
object DiaryTextParser {

    // ── Strict numeric formats ──
    private val strictFormats = listOf(
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    )

    // ── Human-readable formats — case insensitive ("may" and "May" both match) ──
    // Handles: "5 may 2026", "05 May 2026", "5 MMMM 2026", "May 5, 2026"
    private val humanFormats: List<DateTimeFormatter> = listOf(
        "d MMM yyyy",
        "dd MMM yyyy",
        "d MMMM yyyy",
        "dd MMMM yyyy",
        "d MMM, yyyy",
        "MMM d, yyyy",
        "MMM d yyyy",
        "MMMM d, yyyy",
        "MMMM d yyyy",
    ).flatMap { pattern ->
        listOf(Locale.ENGLISH, Locale.getDefault()).distinct().map { locale ->
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(locale)
        }
    }

    fun parse(raw: String): List<ParsedDiary> {
        var currentDate: LocalDate? = null
        val buffer = StringBuilder()
        val result = mutableListOf<ParsedDiary>()

        fun flush() {
            val date = currentDate
            if (date != null && buffer.isNotBlank()) {
                result.add(ParsedDiary(date, buffer.toString().trim()))
            }
        }

        raw.lineSequence().forEach { line ->
            val date = parseDate(line)
            if (date != null) {
                flush()
                currentDate = date
                buffer.clear()
            } else if (currentDate != null) {
                buffer.appendLine(line)
            }
        }
        flush()

        return result
    }

    fun parseDate(line: String): LocalDate? {
        val cleaned = line.trim()
        if (cleaned.isEmpty()) return null

        (strictFormats + humanFormats).forEach { fmt ->
            runCatching { return LocalDate.parse(cleaned, fmt) }
        }
        return null
    }
}
