package com.aburv.kurippidu

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val MONTH_PAGES = 2_400 // 100 years each way
private const val MONTH_ANCHOR = MONTH_PAGES / 2

/**
 * Month calendar. Swipe or use the arrows to change month; long-press the
 * month name to jump to today. Days with an entry get a dot ([dotColors]
 * can colour it, e.g. by mood).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarMonthView(
    currentMonth: YearMonth,
    entryDates: Set<LocalDate>,
    onDateClick: (LocalDate) -> Unit,
    dotColors: Map<LocalDate, Color> = emptyMap(),
    today: LocalDate = LocalDate.now()
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = MONTH_ANCHOR) { MONTH_PAGES }

    fun monthFor(page: Int): YearMonth = currentMonth.plusMonths((page - MONTH_ANCHOR).toLong())
    val shownMonth = monthFor(pagerState.currentPage)

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
            }

            val jumpToToday = {
                scope.launch {
                    val target = MONTH_ANCHOR + ChronoUnit.MONTHS.between(currentMonth, YearMonth.from(today)).toInt()
                    pagerState.scrollToPage(target)
                }
                onDateClick(today)
            }
            Text(
                text = "${shownMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${shownMonth.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .combinedClickable(onClick = {}, onLongClick = { jumpToToday() })
                    .semantics { onLongClick(label = "Go to today") { jumpToToday(); true } }
                    .padding(8.dp)
            )

            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
            }
        }

        Text(
            "Long-press the month to go to today",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { page ->
            MonthGrid(
                month = monthFor(page),
                entryDates = entryDates,
                dotColors = dotColors,
                today = today,
                onDateClick = onDateClick
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    entryDates: Set<LocalDate>,
    dotColors: Map<LocalDate, Color>,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit
) {
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date != null) DayCell(date, date == today, date in entryDates, dotColors[date], onDateClick)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    hasEntry: Boolean,
    dotColor: Color?,
    onDateClick: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .then(
                if (isToday) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                else Modifier
            )
            .clickable { onDateClick(date) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${date.dayOfMonth}",
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontSize = 13.sp
            )
            if (hasEntry) {
                Box(
                    modifier = Modifier
                        .size(if (dotColor != null) 7.dp else 5.dp)
                        .background(dotColor ?: MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}
