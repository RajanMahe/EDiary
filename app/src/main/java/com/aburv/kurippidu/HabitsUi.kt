@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aburv.kurippidu.data.DiaryEntity
import com.aburv.kurippidu.data.Mood
import com.aburv.kurippidu.data.WeeklySummary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/* ---------------- MOOD ---------------- */

/** Five one-tap moods; tapping the chosen one again clears it. */
@Composable
fun MoodRow(selected: Mood?, onSelect: (Mood) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Mood.entries.forEach { mood ->
            val isSelected = mood == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier
                            .background(Color(mood.color).copy(alpha = 0.25f))
                            .border(2.dp, Color(mood.color), CircleShape)
                        else Modifier
                    )
                    .clickable(role = Role.RadioButton) { onSelect(mood) }
                    .semantics {
                        contentDescription = "Mood ${mood.label}"
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(mood.emoji, fontSize = if (isSelected) 24.sp else 20.sp)
            }
        }
    }
}

/* ---------------- ON THIS DAY ---------------- */

@Composable
fun MemoryCard(memory: DiaryEntity, today: LocalDate, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val date = runCatching { LocalDate.parse(memory.date) }.getOrNull() ?: return
    val years = today.year - date.year
    val title = if (years == 1) "1 year ago you wrote" else "$years years ago you wrote"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "🕰️ $title",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    memory.content.replace(Regex("\\s+"), " "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Hide memory", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/* ---------------- REMINDER ---------------- */

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

/** Daily reminder on/off and time. [onSave] gets null to turn it off. */
@Composable
fun ReminderDialog(current: LocalTime?, onSave: (LocalTime?) -> Unit, onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(current != null) }
    var time by remember { mutableStateOf(current ?: LocalTime.of(21, 0)) }
    var pickTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily reminder") },
        text = {
            Column {
                Text(
                    "A gentle nudge to write. Skipped on days you've already written.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me every day", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("At ${time.format(timeFormat)}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (enabled) time else null) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } }
        )
    }
}

fun formatReminder(time: LocalTime?): String = time?.format(timeFormat)?.let { "Every day at $it" } ?: "Off"

/* ---------------- WEEKLY SUMMARY ---------------- */

@Composable
fun WeeklySummarySheet(summary: WeeklySummary?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("This week", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (summary == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            val range = DateTimeFormatter.ofPattern("d MMM")
            Text(
                "${summary.from.format(range)} – ${summary.to.format(range)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Days written", "${summary.daysWritten} / 7", Modifier.weight(1f))
                StatTile("Words", "${summary.words}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Tasks done",
                    if (summary.tasksTotal == 0) "—" else "${summary.tasksDone} / ${summary.tasksTotal}",
                    Modifier.weight(1f)
                )
                StatTile(
                    "Most common mood",
                    summary.topMood?.let { "${it.emoji} ${it.label}" } ?: "—",
                    Modifier.weight(1f)
                )
            }

            if (summary.moodCounts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Moods", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Mood.entries.filter { it in summary.moodCounts }.forEach { mood ->
                        Text("${mood.emoji} ×${summary.moodCounts.getValue(mood)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
