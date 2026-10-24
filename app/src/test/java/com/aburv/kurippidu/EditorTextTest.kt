package com.aburv.kurippidu

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EditorTextTest {

    /* ---------------- WORD COUNT ---------------- */

    @Test
    fun `counts words, not spaces or symbols`() {
        assertEquals(0, wordCount(""))
        assertEquals(0, wordCount("   \n  "))
        assertEquals(3, wordCount("Leg day done"))
        assertEquals(3, wordCount("  Leg\n\nday   done  "))
        assertEquals(2, wordCount("**bold** — word")) // the dash alone isn't a word
        assertEquals(1, wordCount("🏋️ Workout:"))
    }

    /* ---------------- LAST EDITED ---------------- */

    private fun millis(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    @Test
    fun `today shows only the time`() {
        val text = formatEdited(millis(25, 21, 32), now = millis(25, 23, 0))
        assertTrue(text, text.matches(Regex("9:32\\s?(PM|pm|p\\.m\\.)")))
    }

    @Test
    fun `another day shows the date too`() {
        val text = formatEdited(millis(24, 9, 5), now = millis(25, 10, 0))
        assertTrue(text, text.startsWith("24 Oct, 9:05"))
    }

    /* ---------------- SEARCH SNIPPET ---------------- */

    private val highlight = SpanStyle(fontWeight = FontWeight.Bold)

    private fun highlighted(s: androidx.compose.ui.text.AnnotatedString) =
        s.spanStyles.map { s.text.substring(it.start, it.end) }

    @Test
    fun `short text is shown whole with matches highlighted`() {
        val snippet = searchSnippet("Went to the GYM, gym was busy", "gym", highlight)
        assertEquals("Went to the GYM, gym was busy", snippet.text)
        assertEquals(listOf("GYM", "gym"), highlighted(snippet))
    }

    @Test
    fun `long text is cut around the match on word boundaries`() {
        val long = "word ".repeat(40) + "the treadmill broke today " + "more ".repeat(40)
        val snippet = searchSnippet(long, "treadmill", highlight, radius = 20)

        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("…"))
        assertTrue(snippet.text.contains("the treadmill broke"))
        assertEquals(listOf("treadmill"), highlighted(snippet))
        // No half words at the edges
        assertTrue(snippet.text.removePrefix("…").startsWith("word") || snippet.text.removePrefix("…").startsWith("the"))
    }

    @Test
    fun `line breaks become spaces`() {
        assertEquals("first line second line", searchSnippet("first line\n\nsecond line", "second", highlight).text)
    }

    @Test
    fun `no match (for example a date search) shows the beginning`() {
        val snippet = searchSnippet("A".repeat(300), "2026-01", highlight, radius = 10)
        assertEquals("A".repeat(20) + "…", snippet.text)
        assertEquals(emptyList<String>(), highlighted(snippet))
    }
}
