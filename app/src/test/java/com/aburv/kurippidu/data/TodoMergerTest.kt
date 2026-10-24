package com.aburv.kurippidu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoMergerTest {

    private val date = "2026-01-05"

    private fun daily(id: Int, title: String) =
        TodoEntity(id = id, diaryId = 1, title = title, date = date, type = TodoType.DAILY)

    private fun recurring(id: Int, title: String) =
        TodoEntity(
            id = id, diaryId = 1, title = title, date = "2026-01-01",
            type = TodoType.RECURRING, startDate = "2026-01-01", endDate = "2026-01-31"
        )

    private fun override(id: Int, parentId: Int, title: String, isDone: Boolean) =
        TodoEntity(
            id = id, diaryId = 1, title = title, date = date,
            type = TodoType.DAILY_OVERRIDE, isDone = isDone, parentId = parentId
        )

    @Test
    fun `recurring task without a check for the day shows as not done`() {
        val result = mergeTodosForDay(onDate = emptyList(), recurring = listOf(recurring(1, "Gym")))

        assertEquals(listOf(1), result.map { it.id })
        assertFalse(result.single().isDone)
    }

    @Test
    fun `day's check replaces its recurring task and keeps its position`() {
        val result = mergeTodosForDay(
            onDate = listOf(daily(5, "Call mum"), override(9, parentId = 1, title = "Gym", isDone = true)),
            recurring = listOf(recurring(1, "Gym"))
        )

        // Override (id 9) sits where its parent (id 1) was: before the daily task (id 5)
        assertEquals(listOf(9, 5), result.map { it.id })
        assertTrue(result.first().isDone)
    }

    @Test
    fun `same-named recurring tasks are matched by id, not title`() {
        val result = mergeTodosForDay(
            onDate = listOf(override(9, parentId = 2, title = "Gym", isDone = true)),
            recurring = listOf(recurring(1, "Gym"), recurring(2, "Gym"))
        )

        assertEquals(listOf(1, 9), result.map { it.id })
        assertEquals(listOf(false, true), result.map { it.isDone })
    }

    @Test
    fun `check for a task that no longer covers the day is not shown`() {
        val result = mergeTodosForDay(
            onDate = listOf(override(9, parentId = 1, title = "Gym", isDone = true)),
            recurring = emptyList()
        )

        assertEquals(emptyList<TodoEntity>(), result)
    }
}
