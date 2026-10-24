package com.aburv.kurippidu

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Editor tools: formatting and checklist on the left, then things to add
 * (photo, voice, task, recurring task, template). Scrolls sideways on narrow phones.
 */
@Composable
fun EditorToolbar(
    enabled: Boolean,
    onAddType: () -> Unit,
    onFormat: (TextFormat) -> Unit,
    onAddTodoClick: () -> Unit,
    onAddRecurringClick: () -> Unit,
    onManageRecurringClick: () -> Unit,
    onChecklist: () -> Unit = {},
    onAddPhoto: () -> Unit = {},
    onVoice: () -> Unit = {}
) {
    val color = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextTool("Bold", enabled, onClick = { onFormat(TextFormat.Bold) }) {
            Text("B", fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
        }
        TextTool("Italic", enabled, onClick = { onFormat(TextFormat.Italic) }) {
            Text("I", fontStyle = FontStyle.Italic, color = color, fontSize = 18.sp)
        }
        TextTool("Underline", enabled, onClick = { onFormat(TextFormat.Underline) }) {
            Text("U", textDecoration = TextDecoration.Underline, color = color, fontSize = 18.sp)
        }
        TextTool("Checklist", enabled, onClick = onChecklist) {
            Text("☐", color = color, fontSize = 20.sp)
        }

        VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

        IconButton(onClick = onAddPhoto, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_add_photo), contentDescription = "Add photo")
        }
        IconButton(onClick = onVoice, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = "Speak to write")
        }
        IconButton(onClick = onAddTodoClick, enabled = enabled) {
            Icon(Icons.Default.AddCircle, contentDescription = "Add Todo")
        }
        IconButton(onClick = onAddRecurringClick, enabled = enabled) {
            Icon(Icons.Default.DateRange, contentDescription = "Add Recurring Task", tint = color)
        }
        IconButton(onClick = onAddType, enabled = enabled) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
}

@Composable
private fun TextTool(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }
    ) { content() }
}
