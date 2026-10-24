package com.aburv.kurippidu

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * A short piece of [content] around the first match of [query], with every
 * match inside it highlighted. Line breaks become spaces so it fits in a list row.
 * Returns the start of the text when the query isn't found (e.g. a date match).
 */
fun searchSnippet(
    content: String,
    query: String,
    highlight: SpanStyle,
    radius: Int = 50
): AnnotatedString {
    val text = content.replace(Regex("\\s+"), " ").trim()
    val needle = query.trim()
    val first = if (needle.isEmpty()) -1 else text.indexOf(needle, ignoreCase = true)

    if (first < 0) {
        val cut = text.length > radius * 2
        return AnnotatedString(if (cut) text.take(radius * 2).trimEnd() + "…" else text)
    }

    // Widen to whole words around the match
    var start = (first - radius).coerceAtLeast(0)
    var end = (first + needle.length + radius).coerceAtMost(text.length)
    if (start > 0) start = text.indexOf(' ', start).let { if (it in start until first) it + 1 else start }
    if (end < text.length) end = text.lastIndexOf(' ', end).let { if (it > first + needle.length) it else end }

    val window = text.substring(start, end)
    return buildAnnotatedString {
        if (start > 0) append("…")
        var i = 0
        while (i < window.length) {
            val hit = window.indexOf(needle, i, ignoreCase = true)
            if (hit < 0) {
                append(window.substring(i))
                break
            }
            append(window.substring(i, hit))
            withStyle(highlight) { append(window.substring(hit, hit + needle.length)) }
            i = hit + needle.length
        }
        if (end < text.length) append("…")
    }
}
