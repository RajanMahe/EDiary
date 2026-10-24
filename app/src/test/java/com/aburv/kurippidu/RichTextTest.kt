package com.aburv.kurippidu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.aburv.kurippidu.data.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    /* ---------------- TAGS ---------------- */

    @Test
    fun `finds hashtags as lower-case words`() {
        assertEquals(setOf("work", "family", "gym_day"), Tags.extract("Busy #Work day, then #family dinner. #gym_day"))
    }

    @Test
    fun `ignores things that are not tags`() {
        assertEquals(emptySet<String>(), Tags.extract("Room #42, email me#home, C# code, ## heading, #"))
    }

    @Test
    fun `works in other scripts`() {
        assertEquals(setOf("வேலை"), Tags.extract("இன்று #வேலை"))
    }

    @Test
    fun `tag ranges cover the hash too`() {
        val text = "a #work b"
        assertEquals(listOf(2..6), Tags.ranges(text))
        assertEquals("#work", text.substring(2, 7))
    }

    /* ---------------- READ-ONLY RENDERING ---------------- */

    @Test
    fun `reading view hides markers`() {
        assertEquals("make this bold, slanted and underlined", renderMarkdown("make **this** bold, _slanted_ and __underlined__").text)
        assertEquals("my_file_name stays", renderMarkdown("my_file_name stays").text)
    }

    @Test
    fun `reading view styles bold and tags`() {
        val rendered = renderMarkdown("**Big** day #work", Color.Black, tagColor = Color.Red)
        val bold = rendered.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals("Big", rendered.text.substring(bold.start, bold.end))
        val tag = rendered.spanStyles.first { it.item.color == Color.Red }
        assertEquals("#work", rendered.text.substring(tag.start, tag.end))
    }

    @Test
    fun `editor keeps length with tags highlighted`() {
        val text = "**hi** #work"
        val result = MarkdownVisualTransformation(Color.Black, Color.Red)
            .filter(androidx.compose.ui.text.AnnotatedString(text))
        assertEquals(text, result.text.text)
        assertTrue(result.text.spanStyles.any { it.item.color == Color.Red })
    }

    @Test
    fun `printable PDF text drops markers but keeps checklists`() {
        assertEquals("☐ Buy milk\n☑ Call mum, done", DiaryPdf.printable("☐ Buy milk\n☑ **Call mum**, done"))
    }
}
