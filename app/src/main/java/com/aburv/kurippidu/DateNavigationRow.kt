package com.aburv.kurippidu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))

        Text("<<", Modifier.clickable { onMoveDays(-2) }, color = color)
        Spacer(Modifier.width(12.dp))
        Text("<", Modifier.clickable { onMoveDays(-1) }, color = color)

        Spacer(Modifier.width(24.dp))

        Text(
            text = dateText,
            modifier = Modifier.clickable(onClick = onDateClick),
            style = MaterialTheme.typography.titleMedium,
            color = color
        )

        Spacer(Modifier.width(24.dp))

        Text(">", Modifier.clickable { onMoveDays(1) }, color = color)
        Spacer(Modifier.width(12.dp))
        Text(">>", Modifier.clickable { onMoveDays(2) }, color = color)

        Spacer(Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = "Go to Today",
            modifier = Modifier.clickable(onClick = onTodayClick),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(24.dp))
    }
}
