//@file:OptIn(ExperimentalMaterial3Api::class)
//
//package com.aburv.kurippidu
//
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Delete
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import java.time.LocalDate
//import java.time.format.DateTimeFormatter
//
//@Composable
//fun RecurringTaskManagerSheet(
//    viewModel: DiaryViewModel,
//    onDismiss: () -> Unit
//) {
//    LaunchedEffect(Unit) {
//        viewModel.loadRecurringTodos()
//    }
//
//    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
//    var todoToDelete by remember { mutableStateOf<TodoEntity?>(null) }
//
//    ModalBottomSheet(onDismissRequest = onDismiss) {
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp)
//        ) {
//            Text(
//                text = "Recurring Tasks",
//                fontWeight = FontWeight.Bold,
//                fontSize = 20.sp,
//                modifier = Modifier.padding(vertical = 16.dp)
//            )
//
//            if (viewModel.recurringTodos.isEmpty()) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(vertical = 48.dp),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Text(
//                        text = "No recurring tasks yet",
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//                }
//            } else {
//                LazyColumn {
//                    items(viewModel.recurringTodos, key = { it.id }) { todo ->
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(vertical = 8.dp),
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Column(modifier = Modifier.weight(1f)) {
//                                Text(
//                                    text = todo.title,
//                                    fontWeight = FontWeight.Medium,
//                                    fontSize = 15.sp
//                                )
//                                Spacer(Modifier.height(2.dp))
//                                val startFmt = runCatching {
//                                    LocalDate.parse(todo.startDate).format(fmt)
//                                }.getOrDefault(todo.startDate ?: "")
//                                val endFmt = runCatching {
//                                    LocalDate.parse(todo.endDate).format(fmt)
//                                }.getOrDefault(todo.endDate ?: "")
//                                Text(
//                                    text = "$startFmt → $endFmt",
//                                    fontSize = 12.sp,
//                                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                                )
//                            }
//
//                            IconButton(
//                                onClick = { todoToDelete = todo }
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Default.Delete,
//                                    contentDescription = "Delete",
//                                    tint = MaterialTheme.colorScheme.error
//                                )
//                            }
//                        }
//                        Divider()
//                    }
//                }
//            }
//
//            Spacer(Modifier.height(32.dp))
//        }
//    }
//
//    // Confirm delete dialog
//    todoToDelete?.let { todo ->
//        AlertDialog(
//            onDismissRequest = { todoToDelete = null },
//            title = { Text("Delete task?") },
//            text = {
//                Text("\"${todo.title}\" will be removed from all days in its date range.")
//            },
//            confirmButton = {
//                TextButton(
//                    onClick = {
//                        viewModel.deleteRecurringTodo(todo)
//                        todoToDelete = null
//                    }
//                ) {
//                    Text("Delete", color = MaterialTheme.colorScheme.error)
//                }
//            },
//            dismissButton = {
//                TextButton(onClick = { todoToDelete = null }) {
//                    Text("Cancel")
//                }
//            }
//        )
//    }
//}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun RecurringTaskManagerSheet(
    viewModel: DiaryViewModel,
    onDismiss: () -> Unit
) {
    // ✅ FIX 1: reload every time sheet opens
    LaunchedEffect(Unit) {
        viewModel.loadRecurringTodos()
    }

    DisposableEffect(Unit) {
        onDispose { }
    }
    val context = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    var todoToDelete by remember { mutableStateOf<TodoEntity?>(null) }
    var todoToEdit  by remember { mutableStateOf<TodoEntity?>(null) }

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
                    Text(
                        "No recurring tasks yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                Text(
                                    text = todo.title,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                val startFmt = runCatching {
                                    LocalDate.parse(todo.startDate).format(fmt)
                                }.getOrDefault(todo.startDate ?: "—")
                                val endFmt = runCatching {
                                    LocalDate.parse(todo.endDate).format(fmt)
                                }.getOrDefault(todo.endDate ?: "—")
                                Text(
                                    text = "$startFmt → $endFmt",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // ✅ Edit button
                            IconButton(onClick = { todoToEdit = todo }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit dates",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Delete button
                            IconButton(onClick = { todoToDelete = todo }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Divider()
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Delete confirm ──
    todoToDelete?.let { todo ->
        AlertDialog(
            onDismissRequest = { todoToDelete = null },
            title = { Text("Delete task?") },
            text = { Text("\"${todo.title}\" will be removed from all days.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecurringTodo(todo)
                    todoToDelete = null
                    // ✅ FIX 1: list refreshes via deleteRecurringTodo -> loadRecurringTodos
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { todoToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // ── Edit date range ──
    todoToEdit?.let { todo ->
        EditRecurringDateDialog(
            todo = todo,
            onDismiss = { todoToEdit = null },
            onConfirm = { newStart, newEnd ->
                viewModel.updateRecurringTodoDates(todo, newStart, newEnd)
                todoToEdit = null
            }
        )
    }
}

// ── Separate composable for edit dialog to keep state isolated ──
@Composable
private fun EditRecurringDateDialog(
    todo: TodoEntity,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val context = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")


    var showStartPicker by remember { mutableStateOf(false) }  // ✅
    var showEndPicker   by remember { mutableStateOf(false) }  // ✅

    var startDate by remember {
        mutableStateOf(
            runCatching { LocalDate.parse(todo.startDate) }.getOrDefault(LocalDate.now())
        )
    }
    var endDate by remember {
        mutableStateOf(
            runCatching { LocalDate.parse(todo.endDate) }.getOrDefault(LocalDate.now().plusDays(30))
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ✅ FIX 5: Use app-themed date pickers via MaterialDatePicker instead of native dialog
    val startPickerDialog = DatePickerDialog(
        context,
        { _, year, month, day -> startDate = LocalDate.of(year, month + 1, day) },
        startDate.year, startDate.monthValue - 1, startDate.dayOfMonth
    )

    val endPickerDialog = DatePickerDialog(
        context,
        { _, year, month, day -> endDate = LocalDate.of(year, month + 1, day) },
        endDate.year, endDate.monthValue - 1, endDate.dayOfMonth
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit date range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = todo.title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

//                OutlinedButton(
//                    onClick = { startPickerDialog.show() },
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Text("From: ${startDate.format(fmt)}")
//                }
//
//                OutlinedButton(
//                    onClick = { endPickerDialog.show() },
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Text("To: ${endDate.format(fmt)}")
//                }

                OutlinedButton(
                    onClick = { showStartPicker = true },  // use state var
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("From: ${startDate.format(fmt)}")
                }

                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("To: ${endDate.format(fmt)}")
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    endDate.isBefore(startDate) || endDate == startDate ->
                        errorMessage = "End date must be after start date"
                    else -> onConfirm(startDate, endDate)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )


    if (showStartPicker) {
        AppDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showStartPicker = false },
            onDateSelected = { startDate = it; showStartPicker = false }
        )
    }

    if (showEndPicker) {
        AppDatePickerDialog(
            initialDate = endDate,
            onDismiss = { showEndPicker = false },
            onDateSelected = { endDate = it; showEndPicker = false }
        )
    }
}