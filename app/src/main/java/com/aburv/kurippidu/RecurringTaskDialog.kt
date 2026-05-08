//package com.aburv.kurippidu
//
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import java.time.LocalDate
//import java.time.format.DateTimeFormatter
//
//@Composable
//fun RecurringTaskDialog(
//    onDismiss: () -> Unit,
//    onConfirm: (title: String, startDate: LocalDate, endDate: LocalDate) -> Unit
//) {
//    val today = LocalDate.now()
//    var title by remember { mutableStateOf("") }
//    var startDate by remember { mutableStateOf(today) }
//    var endDate by remember { mutableStateOf(today.plusDays(30)) }
//    var showStartPicker by remember { mutableStateOf(false) }
//    var showEndPicker by remember { mutableStateOf(false) }
//    var errorMessage by remember { mutableStateOf<String?>(null) }
//
//    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
//
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("New Recurring Task") },
//        text = {
//            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
//
//                OutlinedTextField(
//                    value = title,
//                    onValueChange = { title = it },
//                    label = { Text("Task name") },
//                    placeholder = { Text("e.g. Gym, Meditation") },
//                    singleLine = true,
//                    modifier = Modifier.fillMaxWidth()
//                )
//
//                // Start date picker
//                OutlinedTextField(
//                    value = startDate.format(fmt),
//                    onValueChange = {},
//                    readOnly = true,
//                    label = { Text("From") },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { showStartPicker = true },
//                    enabled = false
//                )
//
//                // End date picker
//                OutlinedTextField(
//                    value = endDate.format(fmt),
//                    onValueChange = {},
//                    readOnly = true,
//                    label = { Text("To") },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { showEndPicker = true },
//                    enabled = false
//                )
//
//                errorMessage?.let {
//                    Text(it, color = MaterialTheme.colorScheme.error)
//                }
//            }
//        },
//        confirmButton = {
//            TextButton(
//                onClick = {
//                    when {
//                        title.isBlank() -> errorMessage = "Task name is required"
//                        endDate.isBefore(startDate) -> errorMessage = "End date must be after start date"
//                        else -> onConfirm(title.trim(), startDate, endDate)
//                    }
//                }
//            ) { Text("Create") }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss) { Text("Cancel") }
//        }
//    )
//
//    // Use your existing MonthYearPicker or a simple date selector
//    if (showStartPicker) {
//        MonthYearPicker(
//            initialDate = startDate,
//            onDismiss = { showStartPicker = false },
//            onDateSelected = {
//                startDate = it
//                showStartPicker = false
//            }
//        )
//    }
//
//    if (showEndPicker) {
//        MonthYearPicker(
//            initialDate = endDate,
//            onDismiss = { showEndPicker = false },
//            onDateSelected = {
//                endDate = it
//                showEndPicker = false
//            }
//        )
//    }
//}
//
//package com.aburv.kurippidu
//
//import android.app.DatePickerDialog
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import java.time.LocalDate
//import java.time.format.DateTimeFormatter
//
//@Composable
//fun RecurringTaskDialog(
//    onDismiss: () -> Unit,
//    onConfirm: (title: String, startDate: LocalDate, endDate: LocalDate) -> Unit
//) {
//    val context = LocalContext.current
//    val today = LocalDate.now()
//    var title by remember { mutableStateOf("") }
//    var startDate by remember { mutableStateOf(today) }
//    var endDate by remember { mutableStateOf(today.plusDays(30)) }
//    var errorMessage by remember { mutableStateOf<String?>(null) }
//
//    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
//
//    // Native Android date picker for START date
//    val startPickerDialog = DatePickerDialog(
//        context,
//        { _, year, month, day ->
//            startDate = LocalDate.of(year, month + 1, day)
//        },
//        startDate.year,
//        startDate.monthValue - 1,
//        startDate.dayOfMonth
//    )
//
//    // Native Android date picker for END date
//    val endPickerDialog = DatePickerDialog(
//        context,
//        { _, year, month, day ->
//            endDate = LocalDate.of(year, month + 1, day)
//        },
//        endDate.year,
//        endDate.monthValue - 1,
//        endDate.dayOfMonth
//    )
//
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("New Recurring Task") },
//        text = {
//            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
//
//                OutlinedTextField(
//                    value = title,
//                    onValueChange = {
//                        title = it
//                        errorMessage = null
//                    },
//                    label = { Text("Task name") },
//                    placeholder = { Text("e.g. Gym, Meditation") },
//                    singleLine = true,
//                    modifier = Modifier.fillMaxWidth()
//                )
//
//                // Start date — tapping opens native picker
//                OutlinedTextField(
//                    value = startDate.format(fmt),
//                    onValueChange = {},
//                    readOnly = true,
//                    label = { Text("From") },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { startPickerDialog.show() },
//                    enabled = false,
//                    colors = OutlinedTextFieldDefaults.colors(
//                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
//                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
//                        disabledBorderColor = MaterialTheme.colorScheme.outline
//                    )
//                )
//
//                // End date — tapping opens native picker
//                OutlinedTextField(
//                    value = endDate.format(fmt),
//                    onValueChange = {},
//                    readOnly = true,
//                    label = { Text("To") },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { endPickerDialog.show() },
//                    enabled = false,
//                    colors = OutlinedTextFieldDefaults.colors(
//                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
//                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
//                        disabledBorderColor = MaterialTheme.colorScheme.outline
//                    )
//                )
//
//                errorMessage?.let {
//                    Text(it, color = MaterialTheme.colorScheme.error)
//                }
//            }
//        },
//        confirmButton = {
//            TextButton(
//                onClick = {
//                    when {
//                        title.isBlank() -> errorMessage = "Task name is required"
//                        endDate.isBefore(startDate) -> errorMessage = "End date must be after start date"
//                        endDate == startDate -> errorMessage = "End date must be after start date"
//                        else -> onConfirm(title.trim(), startDate, endDate)
//                    }
//                }
//            ) { Text("Create") }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss) { Text("Cancel") }
//        }
//    )
//}


// REPLACE your entire RecurringTaskDialog.kt:
@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecurringTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, startDate: LocalDate, endDate: LocalDate) -> Unit
) {
    val today = LocalDate.now()
    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today.plusDays(30)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Recurring Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

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

                // ✅ FIX 5: OutlinedButton instead of disabled TextField — cleaner tap target
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Text("From: ${startDate.format(fmt)}")
                }

                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
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
                    title.isBlank() -> errorMessage = "Task name is required"
                    endDate.isBefore(startDate) || endDate == startDate ->
                        errorMessage = "End date must be after start date"
                    else -> onConfirm(title.trim(), startDate, endDate)
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    // ✅ Material3 DatePicker — uses your app theme automatically
    if (showStartPicker) {
        AppDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showStartPicker = false },
            onDateSelected = {
                startDate = it
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        AppDatePickerDialog(
            initialDate = endDate,
            onDismiss = { showEndPicker = false },
            onDateSelected = {
                endDate = it
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
    val initialMillis = initialDate
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val picked = Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.systemDefault())
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