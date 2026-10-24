package com.aburv.kurippidu.data

/**
 * Builds the task list shown for one day.
 *
 * @param onDate rows stored on that date: DAILY tasks and DAILY_OVERRIDE rows
 *   (the per-day checked state of a recurring task)
 * @param recurring RECURRING tasks whose date range covers that day
 *
 * Each recurring task is replaced by its override for the day, if one exists,
 * so its checkbox reflects that day only. Overrides keep their parent's
 * position in the list.
 */
fun mergeTodosForDay(
    onDate: List<TodoEntity>,
    recurring: List<TodoEntity>
): List<TodoEntity> {
    val daily = onDate.filter { it.type == TodoType.DAILY }
    val overridesByParent = onDate
        .filter { it.type == TodoType.DAILY_OVERRIDE && it.parentId != null }
        .associateBy { it.parentId }

    val recurringForDay = recurring.map { task -> overridesByParent[task.id] ?: task }

    return (daily + recurringForDay).sortedBy { it.parentId ?: it.id }
}
