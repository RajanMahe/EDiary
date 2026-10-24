@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aburv.kurippidu.data.TodoEntity
import com.aburv.kurippidu.data.Weekdays
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RecurringTaskManagerSheet(
    viewModel: DiaryViewModel,
    onDismiss: () -> Unit
) {
    // viewModel.recurringTodos follows the database; streaks are worked out on open
    LaunchedEffect(viewModel.recurringTodos) { viewModel.loadTaskStreaks() }

    var todoToDelete by remember { mutableStateOf<TodoEntity?>(null) }
    var todoToEdit by remember { mutableStateOf<TodoEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Recurring Tasks",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (viewModel.recurringTodos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recurring tasks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(viewModel.recurringTodos, key = { it.id }) { todo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = todo.title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = scheduleText(todo),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val streak = viewModel.taskStreaks[todo.id] ?: 0
                                if (streak > 0) {
                                    Text(
                                        text = "🔥 $streak in a row",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { todoToEdit = todo }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit dates", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { todoToDelete = todo }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    todoToDelete?.let { todo ->
        AlertDialog(
            onDismissRequest = { todoToDelete = null },
            title = { Text("Delete task?") },
            text = { Text("\"${todo.title}\" will be removed from all days.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecurringTodo(todo)
                    todoToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { todoToDelete = null }) { Text("Cancel") } }
        )
    }

    todoToEdit?.let { todo ->
        EditRecurringDialog(
            todo = todo,
            onDismiss = { todoToEdit = null },
            onConfirm = { schedule ->
                viewModel.updateRecurringTodo(todo, schedule.start, schedule.end, schedule.repeatDays)
                todoToEdit = null
            }
        )
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "5 Jan 2026 → 7 Jan 2026 · Every day", or "→ no end" for open-ended tasks. */
internal fun scheduleText(todo: TodoEntity): String {
    val start = runCatching { LocalDate.parse(todo.startDate).format(dateFormat) }.getOrDefault(todo.startDate ?: "—")
    val end = todo.endDate?.let { runCatching { LocalDate.parse(it).format(dateFormat) }.getOrDefault(it) } ?: "no end"
    return "$start → $end · ${Weekdays.describe(todo.repeatDays)}"
}

@Composable
private fun EditRecurringDialog(
    todo: TodoEntity,
    onDismiss: () -> Unit,
    onConfirm: (RecurringSchedule) -> Unit
) {
    var schedule by remember {
        mutableStateOf(
            RecurringSchedule(
                start = runCatching { LocalDate.parse(todo.startDate) }.getOrDefault(LocalDate.now()),
                end = todo.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                days = Weekdays.daysOf(todo.repeatDays)
            )
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit date range") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(todo.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RecurringScheduleFields(schedule) {
                    schedule = it
                    errorMessage = null
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = schedule.problem()
                if (problem != null) errorMessage = problem else onConfirm(schedule)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
