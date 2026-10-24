package com.aburv.kurippidu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditorToolbar(
    enabled: Boolean,
    onAddType: () -> Unit,
    onFormat: (TextFormat) -> Unit,
    onAddTodoClick: () -> Unit,
    onAddRecurringClick: () -> Unit,
    onManageRecurringClick: () -> Unit,

) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))

        // ✅ ADD BUTTON
        IconButton(
            onClick = onAddTodoClick
        ) {
            Icon(
                imageVector = Icons.Default.AddCircle,
                contentDescription = "Add Todo"
            )
        }

        Spacer(Modifier.width(8.dp))

        Box {
            IconButton(onClick = onAddRecurringClick) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Add Recurring Task",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        IconButton(onClick = onAddType) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
}
