package com.aburv.kurippidu.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A point on a chart: [label] is shown under the first and last points. */
data class ChartPoint(val label: String, val value: Double)

private fun formatValue(value: Double) =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

/**
 * A simple line chart, drawn by hand so the app needs no chart library.
 * The whole series is read out by screen readers through [description].
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    unit: String = ""
) {
    if (points.isEmpty()) return
    val grid = MaterialTheme.colorScheme.outlineVariant
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    val span = (max - min).takeIf { it > 0 } ?: 1.0

    Column(modifier.semantics { contentDescription = description }) {
        Row {
            Text("${formatValue(max)}$unit", style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 6.dp)) {
            val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)
            fun y(v: Double) = (size.height - ((v - min) / span) * size.height).toFloat()

            listOf(0f, size.height / 2, size.height).forEach {
                drawLine(grid, Offset(0f, it), Offset(size.width, it), strokeWidth = 1f)
            }
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = if (points.size == 1) size.width / 2 else i * stepX
                if (i == 0) path.moveTo(x, y(p.value)) else path.lineTo(x, y(p.value))
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            points.forEachIndexed { i, p ->
                val x = if (points.size == 1) size.width / 2 else i * stepX
                drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(x, y(p.value)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${formatValue(min)}$unit", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (points.size > 1) Text(points.last().label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Bars from zero, one per point. */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    unit: String = ""
) {
    if (points.isEmpty()) return
    val max = points.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
    Column(modifier.semantics { contentDescription = description }) {
        Text("${formatValue(max)}$unit", style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 6.dp)) {
            val slot = size.width / points.size
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            points.forEachIndexed { i, p ->
                val h = (p.value / max * size.height).toFloat()
                drawRoundRect(
                    color,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (points.size > 1) Text(points.last().label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
