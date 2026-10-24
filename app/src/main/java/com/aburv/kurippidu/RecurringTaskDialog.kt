@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import com.aburv.kurippidu.data.Weekdays
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What a recurring task repeats on: dates, optional end, and weekdays. */
data class RecurringSchedule(
    val start: LocalDate,
    val end: LocalDate?,          // null = no end date
    val days: Set<DayOfWeek>      // all 7 = every day
) {
    val repeatDays: Int? get() = Weekdays.maskOf(days)

    /** Error message, or null when valid. */
    fun problem(): String? = when {
        days.isEmpty() -> "Choose at least one day"
        end != null && !end.isAfter(start) -> "End date must be after start date"
        else -> null
    }
}

@Composable
fun RecurringTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, startDate: LocalDate, endDate: LocalDate?, repeatDays: Int?) -> Unit
) {
    val today = LocalDate.now()
    var title by remember { mutableStateOf("") }
    var schedule by remember {
        mutableStateOf(RecurringSchedule(today, today.plusDays(30), DayOfWeek.entries.toSet()))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Recurring Task") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Task name") },
                    placeholder = { Text("e.g. Gym, Meditation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                RecurringScheduleFields(schedule) {
                    schedule = it
                    errorMessage = null
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = if (title.isBlank()) "Task name is required" else schedule.problem()
                if (problem != null) errorMessage = problem
                else onConfirm(title.trim(), schedule.start, schedule.end, schedule.repeatDays)
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Start and end dates (or "No end date") and weekday chips; shared by create and edit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringScheduleFields(schedule: RecurringSchedule, onChange: (RecurringSchedule) -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val buttonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth(), colors = buttonColors) {
            Text("From: ${schedule.start.format(fmt)}")
        }
        if (schedule.end != null) {
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth(), colors = buttonColors) {
                Text("To: ${schedule.end.format(fmt)}")
            }
        }
        // The whole row (text too) toggles, not just the small box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = schedule.end == null,
                    role = Role.Checkbox,
                    onValueChange = { noEnd ->
                        onChange(schedule.copy(end = if (noEnd) null else schedule.start.plusDays(30)))
                    }
                )
        ) {
            Checkbox(checked = schedule.end == null, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text("No end date")
        }

        Text("Repeat on", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val on = day in schedule.days
                FilterChip(
                    selected = on,
                    onClick = { onChange(schedule.copy(days = if (on) schedule.days - day else schedule.days + day)) },
                    label = { Text(day.name.take(1) + day.name.substring(1, 3).lowercase()) }
                )
            }
        }
        Text(
            Weekdays.describe(Weekdays.maskOf(schedule.days)).takeIf { schedule.days.isNotEmpty() } ?: "No days chosen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showStartPicker) {
        AppDatePickerDialog(
            initialDate = schedule.start,
            onDismiss = { showStartPicker = false },
            onDateSelected = {
                onChange(schedule.copy(start = it))
                showStartPicker = false
            }
        )
    }
    if (showEndPicker && schedule.end != null) {
        AppDatePickerDialog(
            initialDate = schedule.end,
            onDismiss = { showEndPicker = false },
            onDateSelected = {
                onChange(schedule.copy(end = it))
                showEndPicker = false
            }
        )
    }
}

// ✅ Reusable Material3 themed date picker
@Composable
fun AppDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    // The Material date picker works in UTC days. Converting with the phone's
    // timezone shifted the date by one day east of UTC (e.g. India).
    val initialMillis = initialDate
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val picked = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    onDateSelected(picked)
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}
