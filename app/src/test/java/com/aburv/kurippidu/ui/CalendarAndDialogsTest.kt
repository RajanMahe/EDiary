package com.aburv.kurippidu.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.onRoot
import com.aburv.kurippidu.AppDatePickerDialog
import com.aburv.kurippidu.CalendarMonthView
import com.aburv.kurippidu.RecurringTaskDialog
import com.aburv.kurippidu.ui.theme.DiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import com.aburv.kurippidu.data.Weekdays
import com.aburv.kurippidu.testutil.tap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class CalendarAndDialogsTest {

    // Run in India's timezone (UTC+5:30), where the date-picker bug showed up
    private val originalZone = java.util.TimeZone.getDefault()

    @org.junit.Before
    fun useIndiaTimezone() = java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))

    @org.junit.After
    fun restoreTimezone() = java.util.TimeZone.setDefault(originalZone)

    @get:Rule
    val compose = createComposeRule()

    /* ---------------- CALENDAR ---------------- */

    @Test
    fun `calendar shows the month, moves between months and picks a day`() {
        val picked = mutableListOf<LocalDate>()
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                CalendarMonthView(
                    currentMonth = YearMonth.of(2026, 1),
                    entryDates = setOf(LocalDate.of(2026, 1, 5)),
                    onDateClick = { picked += it }
                )
            }
        }

        compose.onNodeWithText("January 2026").assertExists()
        compose.onNodeWithText("Su").assertExists()

        compose.onNodeWithText("15").performClick()
        assertEquals(listOf(LocalDate.of(2026, 1, 15)), picked)

        compose.onNodeWithContentDescription("Next Month").performClick()
        compose.onNodeWithText("February 2026").assertExists()
        compose.onNodeWithText("28").performClick()
        assertEquals(LocalDate.of(2026, 2, 28), picked.last())

        compose.onNodeWithContentDescription("Previous Month").performClick()
        compose.onNodeWithContentDescription("Previous Month").performClick()
        compose.onNodeWithText("December 2025").assertExists()
    }

    @Test
    fun `long-pressing the month goes to today`() {
        val picked = mutableListOf<LocalDate>()
        val today = LocalDate.of(2026, 10, 25)
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                CalendarMonthView(
                    currentMonth = YearMonth.of(2025, 3),
                    entryDates = emptySet(),
                    onDateClick = { picked += it },
                    today = today
                )
            }
        }
        compose.onNodeWithText("March 2025").performTouchInput { longClick() }
        compose.onNodeWithText("October 2026").assertExists()
        assertEquals(listOf(today), picked)
    }

    @Test
    fun `swiping changes the month`() {
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                CalendarMonthView(YearMonth.of(2026, 1), emptySet(), onDateClick = {})
            }
        }
        // Swipe across the whole width, through the middle of the month grid
        compose.onRoot().performTouchInput { swipeLeft(startX = right - 5f, endX = left + 5f) }
        compose.onNodeWithText("February 2026").assertExists()
    }

    /* ---------------- NEW RECURRING TASK ---------------- */

    private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    @Test
    fun `recurring task needs a name`() {
        var confirmed: List<Any?>? = null
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                RecurringTaskDialog(onDismiss = {}, onConfirm = { t, s, e, d -> confirmed = listOf(t, s, e, d) })
            }
        }

        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Task name is required").assertExists()
        assertNull(confirmed)
    }

    @Test
    fun `recurring task defaults to the next 30 days`() {
        val today = LocalDate.now()
        var confirmed: List<Any?>? = null
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                RecurringTaskDialog(onDismiss = {}, onConfirm = { t, s, e, d -> confirmed = listOf(t, s, e, d) })
            }
        }

        compose.onNodeWithText("From: ${today.format(fmt)}").assertExists()
        compose.onNodeWithText("To: ${today.plusDays(30).format(fmt)}").assertExists()

        compose.onNodeWithText("Task name").performTextInput("  Meditate  ")
        compose.onNodeWithText("Create").performClick()

        assertEquals(listOf("Meditate", today, today.plusDays(30), null), confirmed) // null = every day
    }

    @Test
    fun `recurring task can have no end and repeat on chosen weekdays`() {
        var confirmed: List<Any?>? = null
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                RecurringTaskDialog(onDismiss = {}, onConfirm = { t, s, e, d -> confirmed = listOf(t, s, e, d) })
            }
        }
        compose.onNodeWithText("Task name").performTextInput("Gym")
        compose.onNodeWithText("No end date").tap()
        compose.onNodeWithText("To:", substring = true).assertDoesNotExist()

        // Keep Mon, Wed, Fri only
        listOf("Tue", "Thu", "Sat", "Sun").forEach { compose.onNode(hasText(it) and hasClickAction()).tap() }
        compose.onNodeWithText("Mon, Wed, Fri").assertExists()
        compose.onNodeWithText("Create").performClick()

        val mask = Weekdays.maskOf(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(listOf("Gym", LocalDate.now(), null, mask), confirmed)
    }

    @Test
    fun `recurring task needs at least one day`() {
        var confirmed: List<Any?>? = null
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                RecurringTaskDialog(onDismiss = {}, onConfirm = { t, s, e, d -> confirmed = listOf(t, s, e, d) })
            }
        }
        compose.onNodeWithText("Task name").performTextInput("Gym")
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { compose.onNode(hasText(it) and hasClickAction()).tap() }
        compose.onNodeWithText("Create").performClick()
        compose.onNodeWithText("Choose at least one day").assertExists()
        assertNull(confirmed)
    }

    @Test
    fun `cancel dismisses the recurring dialog`() {
        var dismissed = false
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                RecurringTaskDialog(onDismiss = { dismissed = true }, onConfirm = { _, _, _, _ -> })
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, dismissed)
    }

    @Test
    fun `date fields open a date picker`() {
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                RecurringTaskDialog(onDismiss = {}, onConfirm = { _, _, _, _ -> })
            }
        }
        compose.onNodeWithText("From: ${LocalDate.now().format(fmt)}").performClick()
        // The picker opens on top of the task dialog: both have a Cancel button
        compose.onNodeWithText("OK").assertExists()
        compose.onAllNodesWithText("Cancel").assertCountEquals(2)
    }

    /* ---------------- DATE PICKER ---------------- */

    @Test
    fun `date picker returns the chosen date`() {
        val picked = mutableListOf<LocalDate>()
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                AppDatePickerDialog(
                    initialDate = LocalDate.of(2026, 3, 10),
                    onDismiss = {},
                    onDateSelected = { picked += it }
                )
            }
        }

        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(LocalDate.of(2026, 3, 10)), picked)
    }

    @Test
    fun `date picker cancel calls dismiss`() {
        var dismissed = false
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                AppDatePickerDialog(LocalDate.of(2026, 3, 10), onDismiss = { dismissed = true }, onDateSelected = {})
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, dismissed)
    }
}
