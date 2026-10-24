package com.aburv.kurippidu

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color


class MarkdownVisualTransformation(
    private val textColor: Color = Color.Unspecified
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        parseMarkdown(text.text, builder, textColor)

        // Markers are kept (only faded), so the styled text has exactly the
        // same length as the typed text and Identity mapping is valid.
        return TransformedText(
            builder.toAnnotatedString(),
            OffsetMapping.Identity
        )
    }
}

private fun parseMarkdown(
    text: String,
    builder: AnnotatedString.Builder,
    textColor: Color
) {
    val markerColor =
        if (textColor == Color.Unspecified) Color.Gray else textColor.copy(alpha = 0.3f)
    val markerStyle = SpanStyle(color = markerColor)

    fun span(marker: String, inner: String, style: SpanStyle) {
        builder.withStyle(markerStyle) { append(marker) }
        builder.withStyle(style) { append(inner) }
        builder.withStyle(markerStyle) { append(marker) }
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
