//package com.aburv.kurippidu
//
//import android.text.Layout
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.unit.dp
//import java.time.LocalDate
//import java.time.YearMonth
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.grid.*
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.material3.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.text.style.TextDecoration
//import androidx.compose.ui.Modifier
//
//@Composable
//fun CalendarMonthView(
//    currentMonth: YearMonth,
//    entryDates: List<LocalDate>,
//    onDateClick: (LocalDate) -> Unit
//) {
//    val daysInMonth = currentMonth.lengthOfMonth()
//
//    Column {
//        Text(
//            text = currentMonth.month.name,
//            style = MaterialTheme.typography.titleLarge
//        )
//
//        LazyVerticalGrid(columns = GridCells.Fixed(7)) {
//            items(daysInMonth) { day ->
//                val date = currentMonth.atDay(day + 1)
//                val hasEntry = entryDates.contains(date)
//
//                Box(
//                    modifier = Modifier
//                        .padding(8.dp)
//                        .clickable { onDateClick(date) },
//                    contentAlignment = Alignment.Center
//                ) {
//                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                        Text("${day + 1}")
//
//                        if (hasEntry) {
//                            Box(
//                                modifier = Modifier
//                                    .size(6.dp)
//                                    .background(
//                                        MaterialTheme.colorScheme.primary,
//                                        shape = CircleShape
//                                    )
//                            )
//                        }
//                    }
//                }
//            }
//        }
//    }
//}
//

package com.aburv.kurippidu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarMonthView(
    currentMonth: YearMonth,
    entryDates: List<LocalDate>,
    onDateClick: (LocalDate) -> Unit
) {
    // ✅ FIX: internal mutable state for month navigation
    var displayMonth by remember(currentMonth) { mutableStateOf(currentMonth) }

    val daysInMonth = displayMonth.lengthOfMonth()
    val firstDayOfWeek = displayMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0

    val dayHeaders = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

    Column(modifier = Modifier.padding(16.dp)) {

        // ── Month + Year header with prev/next arrows ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
            }

            Text(
                text = "${displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayMonth.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Day-of-week headers ──
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.heightIn(max = 400.dp),
            userScrollEnabled = false
        ) {
            items(dayHeaders) { day ->
                Text(
                    text = day,
                    modifier = Modifier.padding(4.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Empty cells before the 1st of the month
            items(firstDayOfWeek) {
                Box(modifier = Modifier.padding(8.dp))
            }

            // Day cells
            items(daysInMonth) { dayIndex ->
                val date = displayMonth.atDay(dayIndex + 1)
                val hasEntry = entryDates.contains(date)
                val isToday = date == LocalDate.now()

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .then(
                            if (isToday) Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                CircleShape
                            ) else Modifier
                        )
                        .clickable { onDateClick(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${dayIndex + 1}",
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp
                        )
                        if (hasEntry) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
