package com.aburv.kurippidu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DiaryTextParserTest {

    @Test
    fun `splits text into one entry per date line`() {
        val raw = """
            2026-01-01
            New year.
            Went for a walk.

            02-01-2026
            Day two.
        """.trimIndent()

        val entries = DiaryTextParser.parse(raw)

        assertEquals(
            listOf(
                ParsedDiary(LocalDate.of(2026, 1, 1), "New year.\nWent for a walk."),
                ParsedDiary(LocalDate.of(2026, 1, 2), "Day two.")
            ),
            entries
        )
    }

    @Test
    fun `understands all supported date formats`() {
        val expected = LocalDate.of(2026, 5, 5)
        listOf(
            "05-05-2026",
            "2026-05-05",
            "05.05.2026",
            "05/05/2026",
            "5 May 2026",
            "05 may 2026",
            "5 MAY 2026",
            "5 May, 2026",
            "May 5, 2026",
            "May 5 2026",
            "  5 May 2026  ",
        ).forEach { line ->
            assertEquals("for \"$line\"", expected, DiaryTextParser.parseDate(line))
        }
    }

    @Test
    fun `ordinary text is not mistaken for a date`() {
        listOf("", "Went to the gym", "May was fun", "2026", "32-01-2026").forEach { line ->
            assertNull("for \"$line\"", DiaryTextParser.parseDate(line))
        }
    }

    @Test
    fun `ignores text before the first date and days with no text`() {
        val raw = """
            Exported from my notes app
            2026-03-01

            2026-03-02
            Only this day has text
        """.trimIndent()

        val entries = DiaryTextParser.parse(raw)

        assertEquals(listOf(ParsedDiary(LocalDate.of(2026, 3, 2), "Only this day has text")), entries)
    }

    @Test
    fun `empty input gives no entries`() {
        assertEquals(emptyList<ParsedDiary>(), DiaryTextParser.parse(""))
    }
}
