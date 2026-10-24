package com.aburv.kurippidu.data

import com.aburv.kurippidu.reminders.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class HabitsTest {

    // 2026-01-05 is a Monday
    private val monday = LocalDate.of(2026, 1, 5)

    /* ---------------- WEEKDAYS ---------------- */

    @Test
    fun `weekday masks`() {
        assertNull(Weekdays.maskOf(emptySet()))
        assertNull(Weekdays.maskOf(java.time.DayOfWeek.entries))
        val mwf = Weekdays.maskOf(setOf(MONDAY, WEDNESDAY, FRIDAY))!!
        assertEquals(setOf(MONDAY, WEDNESDAY, FRIDAY), Weekdays.daysOf(mwf))
        assertTrue(Weekdays.repeatsOn(mwf, monday))
        assertFalse(Weekdays.repeatsOn(mwf, monday.plusDays(1)))
        assertTrue(Weekdays.repeatsOn(null, monday.plusDays(1)))
    }

    @Test
    fun `weekday descriptions`() {
        assertEquals("Every day", Weekdays.describe(null))
        assertEquals("Weekends", Weekdays.describe(Weekdays.maskOf(setOf(SATURDAY, SUNDAY))))
        assertEquals("Weekdays", Weekdays.describe(Weekdays.maskOf(java.time.DayOfWeek.entries - setOf(SATURDAY, SUNDAY))))
        assertEquals("Mon, Wed, Fri", Weekdays.describe(Weekdays.maskOf(setOf(FRIDAY, MONDAY, WEDNESDAY))))
    }

    /* ---------------- WRITING STREAK ---------------- */

    @Test
    fun `writing streak counts back from today`() {
        val written = setOf(monday, monday.minusDays(1), monday.minusDays(2), monday.minusDays(4))
        assertEquals(3, writingStreak(written, monday))
    }

    @Test
    fun `not written yet today keeps yesterday's streak`() {
        assertEquals(2, writingStreak(setOf(monday.minusDays(1), monday.minusDays(2)), monday))
    }

    @Test
    fun `a missed day ends the streak`() {
        assertEquals(0, writingStreak(setOf(monday.minusDays(2)), monday))
        assertEquals(0, writingStreak(emptySet(), monday))
    }

    /* ---------------- TASK STREAK ---------------- */

    private fun recurring(start: LocalDate, mask: Int? = null) = TodoEntity(
        id = 1, diaryId = 1, title = "Gym", date = start.toString(), type = TodoType.RECURRING,
        startDate = start.toString(), endDate = null, repeatDays = mask
    )

    @Test
    fun `task streak skips days the task isn't scheduled`() {
        val mwf = Weekdays.maskOf(setOf(MONDAY, WEDNESDAY, FRIDAY))
        val task = recurring(monday.minusDays(14), mwf)
        // Ticked last Wed and Fri; today (Mon) not yet
        val done = setOf(monday.minusDays(5), monday.minusDays(3))
        assertEquals(2, taskStreak(task, done, monday))
        // Ticked today too
        assertEquals(3, taskStreak(task, done + monday, monday))
    }

    @Test
    fun `task streak stops at a missed scheduled day and at the start date`() {
        val task = recurring(monday.minusDays(3))
        assertEquals(1, taskStreak(task, setOf(monday.minusDays(1), monday.minusDays(3)), monday))
        assertEquals(4, taskStreak(task, (0..3).map { monday.minusDays(it.toLong()) }.toSet(), monday))
    }

    /* ---------------- RECURRING TASKS ---------------- */

    @Test
    fun `open-ended task covers any later day`() {
        val task = recurring(monday)
        assertTrue(task.coversDate(monday.plusYears(3)))
        assertFalse(task.coversDate(monday.minusDays(1)))
        assertFalse(task.copy(endDate = monday.plusDays(1).toString()).coversDate(monday.plusDays(2)))
    }

    @Test
    fun `day list hides recurring tasks on days they don't repeat`() {
        val task = recurring(monday, Weekdays.maskOf(setOf(MONDAY)))
        assertEquals(1, mergeTodosForDay(emptyList(), listOf(task), monday).size)
        assertEquals(0, mergeTodosForDay(emptyList(), listOf(task), monday.plusDays(1)).size)
    }

    /* ---------------- WEEKLY SUMMARY ---------------- */

    @Test
    fun `weekly summary adds up the last 7 days`() {
        fun entry(days: Long, text: String, mood: Mood?) =
            DiaryEntity(diaryOwnerId = 1, date = monday.minusDays(days).toString(), content = text, mood = mood?.key)
        val entries = listOf(
            entry(0, "one two three", Mood.GOOD),
            entry(1, "four five", Mood.GOOD),
            entry(2, "", Mood.SAD), // mood only, no writing
            entry(9, "too old to count", Mood.GREAT)
        )
        val daily = listOf(
            TodoEntity(id = 10, diaryId = 1, title = "Call", date = monday.toString(), type = TodoType.DAILY, isDone = true),
            TodoEntity(id = 11, diaryId = 1, title = "Shop", date = monday.toString(), type = TodoType.DAILY)
        )
        val gym = recurring(monday.minusDays(2)) // 3 scheduled days in the week
        val tick = TodoEntity(
            id = 12, diaryId = 1, title = "Gym", date = monday.minusDays(1).toString(),
            type = TodoType.DAILY_OVERRIDE, isDone = true, parentId = 1
        )

        val summary = weeklySummary(entries, daily + tick, listOf(gym), monday)

        assertEquals(monday.minusDays(6), summary.from)
        assertEquals(2, summary.daysWritten)
        assertEquals(5, summary.words)
        assertEquals(5, summary.tasksTotal) // 2 daily + gym on 3 days
        assertEquals(2, summary.tasksDone)
        assertEquals(Mood.GOOD, summary.topMood)
        assertEquals(mapOf(Mood.GOOD to 2, Mood.SAD to 1), summary.moodCounts)
    }

    @Test
    fun `empty week`() {
        val summary = weeklySummary(emptyList(), emptyList(), emptyList(), monday)
        assertEquals(0, summary.daysWritten)
        assertEquals(0, summary.tasksTotal)
        assertNull(summary.topMood)
    }

    /* ---------------- REMINDER TIMING ---------------- */

    @Test
    fun `reminder later today or tomorrow`() {
        val now = LocalDateTime.of(2026, 1, 5, 20, 0)
        assertEquals(Duration.ofMinutes(90), Reminders.delayUntilNext(LocalTime.of(21, 30), now))
        assertEquals(Duration.ofHours(23), Reminders.delayUntilNext(LocalTime.of(19, 0), now))
        assertEquals(Duration.ofHours(24), Reminders.delayUntilNext(LocalTime.of(20, 0), now))
    }

    @Test
    fun `moods are stored by key`() {
        assertEquals(Mood.SAD, Mood.fromKey("sad"))
        assertNull(Mood.fromKey("angry"))
        assertNull(Mood.fromKey(null))
    }
}
