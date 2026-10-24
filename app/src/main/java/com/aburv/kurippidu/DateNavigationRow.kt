package com.aburv.kurippidu

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The "<< < date > >>" row under the top bar, plus the jump-to-today icon. */
@Composable
fun DateNavigationRow(
    dateText: String,
    color: Color,
    onMoveDays: (days: Int) -> Unit,
    onDateClick: () -> Unit,
    onTodayClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(4.dp))
        // 48 dp buttons: easy to hit, unlike the old bare text
        StepButton("<<", "Back 2 days", color) { onMoveDays(-2) }
        StepButton("<", "Previous day", color) { onMoveDays(-1) }

        TextButton(onClick = onDateClick, modifier = Modifier.weight(1f)) {
            Text(dateText, style = MaterialTheme.typography.titleMedium, color = color)
        }

        StepButton(">", "Next day", color) { onMoveDays(1) }
        StepButton(">>", "Forward 2 days", color) { onMoveDays(2) }

        IconButton(onClick = onTodayClick) {
            Icon(Icons.Default.DateRange, contentDescription = "Go to Today", tint = color)
        }
    }
}

@Composable
private fun StepButton(label: String, description: String, color: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold)
    }
}
