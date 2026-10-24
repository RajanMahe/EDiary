package com.aburv.kurippidu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.aburv.kurippidu.data.Tags

/** Editor styling: markers stay (faded) so the text length never changes. */
class MarkdownVisualTransformation(
    private val textColor: Color = Color.Unspecified,
    private val tagColor: Color = Color.Unspecified
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        parseMarkdown(text.text, builder, textColor, keepMarkers = true)
        styleTags(builder, text.text, tagColor)

        // Markers are kept (only faded), so the styled text has exactly the
        // same length as the typed text and Identity mapping is valid.
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** Read-only styling: markers are hidden, so the page reads like a printed diary. */
fun renderMarkdown(text: String, textColor: Color = Color.Unspecified, tagColor: Color = Color.Unspecified): AnnotatedString {
    val builder = AnnotatedString.Builder()
    parseMarkdown(text, builder, textColor, keepMarkers = false)
    val plain = builder.toAnnotatedString()
    // Tags are found on the text as shown (markers removed)
    val withTags = AnnotatedString.Builder(plain)
    styleTags(withTags, plain.text, tagColor)
    return withTags.toAnnotatedString()
}

private fun styleTags(builder: AnnotatedString.Builder, text: String, tagColor: Color) {
    if (tagColor == Color.Unspecified) return
    Tags.ranges(text).forEach { range ->
        builder.addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
    }
}

private fun parseMarkdown(
    text: String,
    builder: AnnotatedString.Builder,
    textColor: Color,
    keepMarkers: Boolean
) {
    val markerColor =
        if (textColor == Color.Unspecified) Color.Gray else textColor.copy(alpha = 0.3f)
    val markerStyle = SpanStyle(color = markerColor)

    fun span(marker: String, inner: String, style: SpanStyle) {
        if (keepMarkers) builder.withStyle(markerStyle) { append(marker) }
        builder.withStyle(style) { append(inner) }
        if (keepMarkers) builder.withStyle(markerStyle) { append(marker) }
    }

    var i = 0

    while (i < text.length) {
        when {
            // 1️⃣ BOLD
            text.startsWith("**", i) && text.indexOf("**", i + 2) > i + 2 -> {
                val end = text.indexOf("**", i + 2)
                span("**", text.substring(i + 2, end), SpanStyle(fontWeight = FontWeight.Bold))
                i = end + 2
            }

            // 2️⃣ UNDERLINE (must come BEFORE italic)
            text.startsWith("__", i) && text.indexOf("__", i + 2) > i + 2 -> {
                val end = text.indexOf("__", i + 2)
                span(
                    "__",
                    text.substring(i + 2, end),
                    SpanStyle(textDecoration = TextDecoration.Underline, color = textColor)
                )
                i = end + 2
            }

            // 3️⃣ ITALIC — only when "_" starts a word, so my_file_name stays plain
            text[i] == '_' &&
                    (i == 0 || !text[i - 1].isLetterOrDigit()) &&
                    text.indexOf('_', i + 1) > i + 1 -> {
                val end = text.indexOf('_', i + 1)
                span("_", text.substring(i + 1, end), SpanStyle(fontStyle = FontStyle.Italic))
                i = end + 1
            }

            else -> {
                builder.append(text[i])
                i++
            }
        }
    }
}
