package com.example.diary

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset

fun Modifier.ruledBackground(
    lineColor: Color = Color(0xFFE0E0E0),
    lineSpacing: Dp = 24.5.dp
): Modifier = this.drawBehind {
    val spacingPx = lineSpacing.toPx()
    var y = spacingPx

    while (y < size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += spacingPx
    }
}
