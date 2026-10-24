package com.aburv.kurippidu.data

/**
 * Builds the task list shown for one day.
 *
 * @param onDate rows stored on that date: DAILY tasks and DAILY_OVERRIDE rows
 *   (the per-day checked state of a recurring task)
 * @param recurring RECURRING tasks whose date range covers that day
 * @param date the day, used to skip weekdays a task doesn't repeat on
 *
 * Each recurring task is replaced by its override for the day, if one exists,
 * so its checkbox reflects that day only. Overrides keep their parent's
 * position in the list.
 */
fun mergeTodosForDay(
    onDate: List<TodoEntity>,
    recurring: List<TodoEntity>,
    date: java.time.LocalDate? = null
): List<TodoEntity> {
    val daily = onDate.filter { it.type == TodoType.DAILY }
    val overridesByParent = onDate
        .filter { it.type == TodoType.DAILY_OVERRIDE && it.parentId != null }
        .associateBy { it.parentId }

    // Tasks set to repeat only on some weekdays are hidden on the others
    val recurringForDay = recurring
        .filter { date == null || Weekdays.repeatsOn(it.repeatDays, date) }
        .map { task -> overridesByParent[task.id] ?: task }

    return (daily + recurringForDay).sortedBy { it.parentId ?: it.id }
}
