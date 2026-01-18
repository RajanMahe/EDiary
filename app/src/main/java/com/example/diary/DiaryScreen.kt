@file:OptIn(ExperimentalMaterial3Api::class)


package com.example.diary



import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.DateRange
import androidx.lifecycle.viewmodel.compose.viewModel






@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen() {

    /* ---------------- DATE STATE ---------------- */
    val iconColor = Color.Black
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showCalendarPicker by remember { mutableStateOf(false) }

    val diaryViewModel: DiaryViewModel = viewModel()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }
    val dateText = if (selectedDate == LocalDate.now()) {
        "Today (${selectedDate.format(dateFormatter)})"
    } else {
        selectedDate.format(dateFormatter)
    }

    /* ---------------- DIARY TABS STATE ---------------- */

// ✅ LOAD diary WHEN date changes

    LaunchedEffect(selectedDate) {
        diaryViewModel.saveCurrentDiary()
        diaryViewModel.loadDiaryForDate(selectedDate)
    }

    /* ---------------- TOOLBAR STATE ---------------- */

    var showTypeDialog by remember { mutableStateOf(false) }

    /* ---------------- UI ---------------- */

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF4285F4)
                ),
                title = {
                    Text(
                        text = "EDiary",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            /* -------- DATE ROW (below blue bar) -------- */

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically

            ) {
                Spacer(Modifier.weight(1f))

                Text("<<",Modifier.clickable { selectedDate = selectedDate.minusDays(2) },color = iconColor)
                Spacer(Modifier.width(12.dp))
                Text("<", Modifier.clickable { selectedDate = selectedDate.minusDays(1) },color = iconColor)

                Spacer(Modifier.width(24.dp))

                Text(
                    text = dateText,
                    modifier = Modifier.clickable { showCalendarPicker = true },
                    style = MaterialTheme.typography.titleMedium,
                    color = iconColor
                )

                Spacer(Modifier.width(24.dp))

                Text(">", Modifier.clickable { selectedDate = selectedDate.plusDays(1) },color = iconColor)
                Spacer(Modifier.width(12.dp))
                Text(">>", Modifier.clickable { selectedDate = selectedDate.plusDays(2) },color = iconColor)

                Spacer(Modifier.weight(1f))

                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Go to Today",
                    modifier = Modifier.clickable {
                        selectedDate = LocalDate.now()
                    },
                    tint = iconColor
                )
            }


            Divider(
                color = Color(0xFF4285F4),
                thickness = 1.dp
            )


// --- Editor Toolbar ---
            EditorToolbar {
                showTypeDialog = true
            }

            Divider(
                color = Color(0xFF4285F4),
                thickness = 1.dp
            )
// --- Ruled Editor ---

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .ruledBackground()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = diaryViewModel.diaryText,
                    onValueChange = {
                        diaryViewModel.updateDiaryText(it)                    },
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.Black,
                        lineHeight = 24.sp
                    ),
                    modifier = Modifier.fillMaxSize()
                )

                if (diaryViewModel.diaryText.text.isEmpty()) {
                    Text(
                        text = "Add to a type",
                        color = Color.Gray,
                        modifier = Modifier.alpha(0.5f)
                    )
                }

            }

            /* -------- TYPE PICKER -------- */

            if (showTypeDialog) {
                ModalBottomSheet(onDismissRequest = { showTypeDialog = false }) {
                    listOf("Food", "Workout", "Spent").forEach { type ->
                        ListItem(
                            headlineContent = { Text(type) },
                            modifier = Modifier.clickable {
                                showTypeDialog = false
                                // insertion logic next step
                            }
                        )
                    }
                }
            }
        }
    }
    if (showCalendarPicker) {
        MonthYearPicker(
            initialDate = selectedDate,
            onDismiss = { showCalendarPicker = false },
            onDateSelected = { newDate ->
                selectedDate = newDate
                showCalendarPicker = false
            }
        )
    }
}

@Composable
fun MonthYearPicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val months = listOf(
        "Jan","Feb","Mar","Apr","May","Jun",
        "Jul","Aug","Sep","Oct","Nov","Dec"
    )

    var selectedYear by remember { mutableStateOf(initialDate.year) }
    var selectedMonth by remember { mutableStateOf(initialDate.monthValue - 1) }

    ModalBottomSheet(onDismissRequest = onDismiss) {

        // YEAR SELECTOR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selectedYear-- }) {
                Text("−", fontSize = 24.sp)
            }

            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            IconButton(onClick = { selectedYear++ }) {
                Text("+", fontSize = 24.sp)
            }
        }

        Divider(color = Color(0xFF4285F4))

        // MONTH GRID
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            months.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                    row.forEachIndexed { colIndex, month ->
                        val index = rowIndex * 3 + colIndex
                        val isSelected = index == selectedMonth
                        Text(
                            text = month,
                            modifier = Modifier
                                .padding(12.dp)
                                .clickable {
                                    selectedMonth = index
                                    onDateSelected(
                                        LocalDate.of(
                                            selectedYear,
                                            selectedMonth + 1,
                                            minOf(
                                                initialDate.dayOfMonth,
                                                LocalDate.of(
                                                    selectedYear,
                                                    selectedMonth + 1,
                                                    1
                                                ).lengthOfMonth()
                                            )
                                        )
                                    )
                                },
                            color = if (isSelected)
                                Color(0xFF4285F4)
                            else
                                Color.Black,
                            fontWeight = if (isSelected)
                                FontWeight.Bold
                            else
                                FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorToolbar(
    onAddType: () -> Unit
) {
    val iconColor = Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Normal", color = iconColor ,modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = { /* bold later */ }) {
            Text("B", color = iconColor ,fontWeight = FontWeight.Bold, )
        }
        IconButton(onClick = { /* italic later */ }) {
            Text("I", color = iconColor,fontStyle = FontStyle.Italic)
        }
        IconButton(onClick = { /* underline later */ }) {
            Text("U", color = iconColor,textDecoration = TextDecoration.Underline)
        }
        IconButton(onClick = { /* color later */ }) {
            Text("🎨")
        }
        IconButton(onClick = { /* bullet later */ }) {
            Text("•",color = iconColor)
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onAddType) {
            Icon(Icons.Default.Add,contentDescription = "Add type",tint=iconColor)
        }
    }
}
