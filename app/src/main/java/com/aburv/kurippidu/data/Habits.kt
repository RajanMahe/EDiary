package com.aburv.kurippidu.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Weekday sets stored as a bit mask: Monday = bit 0 … Sunday = bit 6. null means every day. */
object Weekdays {
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun maskOf(days: Collection<DayOfWeek>): Int? =
        if (days.isEmpty() || days.size == 7) null else days.fold(0) { mask, d -> mask or bit(d) }

    fun daysOf(mask: Int?): Set<DayOfWeek> =
        if (mask == null) DayOfWeek.entries.toSet() else DayOfWeek.entries.filter { mask and bit(it) != 0 }.toSet()

    fun repeatsOn(mask: Int?, date: LocalDate): Boolean = mask == null || mask and bit(date.dayOfWeek) != 0

    /** "Every day", "Weekdays", "Weekends" or e.g. "Mon, Wed, Fri". */
    fun describe(mask: Int?): String {
        val days = daysOf(mask)
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        return when {
            days.size == 7 -> "Every day"
            days == DayOfWeek.entries.toSet() - weekend -> "Weekdays"
            days == weekend -> "Weekends"
            else -> days.sorted().joinToString(", ") { it.name.take(1) + it.name.substring(1, 3).lowercase() }
        }
    }
}

/**
 * Days in a row with writing, counting back from [today].
 * If today has nothing yet the streak is still alive and counts from yesterday.
 */
fun writingStreak(written: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in written) today else today.minusDays(1)
    var count = 0
    while (day in written) {
        count++
        day = day.minusDays(1)
    }
    return count
}

/**
 * Scheduled days in a row that a recurring task was ticked, counting back from
 * [today] (today not ticked yet doesn't break it). Days it isn't scheduled are skipped.
 */
fun taskStreak(task: TodoEntity, doneDates: Set<LocalDate>, today: LocalDate): Int {
    val start = task.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 0
    var day = today
    var count = 0
    var first = true
    while (!day.isBefore(start)) {
        if (Weekdays.repeatsOn(task.repeatDays, day)) {
            when {
                day in doneDates -> count++
                first && day == today -> Unit // today still open
                else -> return count
            }
            first = false
        }
        day = day.minusDays(1)
    }
    return count
}

data class WeeklySummary(
    val from: LocalDate,
    val to: LocalDate,
    val daysWritten: Int,
    val words: Int,
    val tasksDone: Int,
    val tasksTotal: Int,
    val topMood: Mood?,
    val moodCounts: Map<Mood, Int>
)

/**
 * The last 7 days up to [today]: how many days had writing, words written,
 * tasks ticked (each recurring task counts once per scheduled day) and the
 * most common mood.
 */
fun weeklySummary(
    entries: List<DiaryEntity>,
    todos: List<TodoEntity>,
    recurring: List<TodoEntity>,
    today: LocalDate
): WeeklySummary {
    val from = today.minusDays(6)
    val days = (0..6).map { from.plusDays(it.toLong()) }
    val inWeek = entries.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d in days } == true }

    val written = inWeek.filter { it.content.isNotBlank() }
    val words = written.sumOf { e -> e.content.split(Regex("\\s+")).count { w -> w.any(Char::isLetterOrDigit) } }

    var total = 0
    var done = 0
    days.forEach { day ->
        val onDay = todos.filter { it.date == day.toString() }
        mergeTodosForDay(onDay, recurring.filter { it.coversDate(day) }, day).forEach { task ->
            total++
            if (task.isDone) done++
        }
    }

    val moods = inWeek.mapNotNull { Mood.fromKey(it.mood) }.groupingBy { it }.eachCount()
    // Ties go to the more positive mood
    val top = moods.entries.sortedWith(compareByDescending<Map.Entry<Mood, Int>> { it.value }.thenBy { it.key.ordinal })
        .firstOrNull()?.key

    return WeeklySummary(from, today, written.size, words, done, total, top, moods)
}

/** Recurring task's date range (no end date = open-ended) includes [date]. */
fun TodoEntity.coversDate(date: LocalDate): Boolean {
    val start = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    val end = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return !date.isBefore(start) && (end == null || !date.isAfter(end))
}
