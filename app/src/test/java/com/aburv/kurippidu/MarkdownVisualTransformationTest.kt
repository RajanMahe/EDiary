package com.aburv.kurippidu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownVisualTransformationTest {

    private val transformation = MarkdownVisualTransformation(Color.Black)

    private fun transform(text: String) = transformation.filter(AnnotatedString(text))

    private fun styledRange(text: String, predicate: (androidx.compose.ui.text.SpanStyle) -> Boolean) =
        transform(text).text.spanStyles.filter { predicate(it.item) }.map { it.start until it.end }

    @Test
    fun `text is never shortened, so the cursor can go anywhere`() {
        listOf(
            "**hello**",
            "_hi_",
            "__under__",
            "my_file_name",
            "**unclosed",
            "****",
            "a _b_ **c** __d__ e",
            ""
        ).forEach { input ->
            val result = transform(input)
            assertEquals("for \"$input\"", input, result.text.text)

            // The old crash: mapping the end of the text must stay in range
            for (offset in 0..input.length) {
                assertEquals(offset, result.offsetMapping.originalToTransformed(offset))
                assertEquals(offset, result.offsetMapping.transformedToOriginal(offset))
            }
        }
    }

    @Test
    fun `bold text between double stars is bold`() {
        val bold = styledRange("say **hi** now") { it.fontWeight == FontWeight.Bold }
        assertEquals(listOf(6 until 8), bold) // "hi"
    }

    @Test
    fun `underline between double underscores is underlined`() {
        val underlined = styledRange("__ok__") { it.textDecoration == TextDecoration.Underline }
        assertEquals(listOf(2 until 4), underlined)
    }

    @Test
    fun `italic between single underscores at a word start`() {
        val italic = styledRange("so _very_ good") { it.fontStyle == FontStyle.Italic }
        assertEquals(listOf(4 until 8), italic)
    }

    @Test
    fun `underscores inside words do not italicise`() {
        val italic = styledRange("my_file_name") { it.fontStyle == FontStyle.Italic }
        assertTrue(italic.isEmpty())
    }

    @Test
    fun `markers are kept but faded`() {
        val faded = styledRange("**hi**") { it.color != Color.Unspecified && it.color.alpha < 1f }
        assertEquals(listOf(0 until 2, 4 until 6), faded)
    }

    @Test
    fun `unspecified text colour still fades markers`() {
        val result = MarkdownVisualTransformation().filter(AnnotatedString("**hi**"))
        assertEquals("**hi**", result.text.text)
        assertTrue(result.text.spanStyles.any { it.item.color == Color.Gray })
    }
}
