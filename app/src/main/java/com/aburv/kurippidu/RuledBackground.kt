package com.aburv.kurippidu

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Notebook lines. With [lineSpacing] equal to the text's line height and
 * [topOffset] equal to the text's top padding, each line sits under a row of text.
 */
fun Modifier.ruledBackground(
    lineColor: Color,
    lineSpacing: Dp = 24.5.dp,
    topOffset: Dp = 0.dp,
    strokeWidth: Float = 1f
): Modifier = this.drawBehind {
    val spacingPx = lineSpacing.toPx()
    if (spacingPx <= 0f) return@drawBehind
    var y = topOffset.toPx() + spacingPx

    while (y < size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth
        )
        y += spacingPx
    }
}
