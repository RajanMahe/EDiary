package com.example.diary

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color


class MarkdownVisualTransformation(
    private val textColor: Color = Color.Unspecified
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        parseMarkdown(text.text, builder, textColor)

        return TransformedText(
            builder.toAnnotatedString(),
            OffsetMapping.Identity   // ✅ THIS PREVENTS CRASH
        )
    }
}

private fun parseMarkdown(
    text: String,
    builder: AnnotatedString.Builder,
    textColor: Color

) {
    var i = 0

    while (i < text.length) {
        when {
            // 1️⃣ BOLD
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(text.substring(i + 2, end))
                    builder.pop()
                    i = end + 2
                } else {
                    builder.append("**")
                    i += 2
                }
            }

            // 2️⃣ UNDERLINE (must come BEFORE italic)
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    builder.pushStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline,
                            color = textColor
                        )

                    )
                    builder.append(text.substring(i + 2, end))
                    builder.pop()
                    i = end + 2
                } else {
                    builder.append("__")
                    i += 2
                }
            }

            // 3️⃣ ITALIC (single underscore LAST)
            text.startsWith("_", i) -> {
                val end = text.indexOf("_", i + 1)
                if (end != -1) {
                    builder.pushStyle(
                        SpanStyle(fontStyle = FontStyle.Italic)
                    )
                    builder.append(text.substring(i + 1, end))
                    builder.pop()
                    i = end + 1
                } else {
                    builder.append("_")
                    i++
                }
            }

            else -> {
                builder.append(text[i])
                i++
            }
        }
    }
}
