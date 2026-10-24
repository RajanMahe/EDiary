package com.aburv.kurippidu.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.aburv.kurippidu.AppLockToggle
import com.aburv.kurippidu.DateNavigationRow
import com.aburv.kurippidu.DiaryPage
import com.aburv.kurippidu.EditorToolbar
import com.aburv.kurippidu.EmptyDayPlaceholder
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.data.TodoEntity
import com.aburv.kurippidu.data.TodoType
import com.aburv.kurippidu.ui.theme.DiaryTheme
import com.aburv.kurippidu.testutil.tap
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    /* ---------------- DATE ROW ---------------- */

    @Test
    fun `date row buttons report how many days to move`() {
        val moves = mutableListOf<Int>()
        var dateClicks = 0
        var todayClicks = 0
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                DateNavigationRow(
                    dateText = "Today (5 Jan)",
                    color = Color.Black,
                    onMoveDays = { moves += it },
                    onDateClick = { dateClicks++ },
                    onTodayClick = { todayClicks++ }
                )
            }
        }

        compose.onNodeWithText("<<").performClick()
        compose.onNodeWithText("<").performClick()
        compose.onNodeWithText(">").performClick()
        compose.onNodeWithText(">>").performClick()
        compose.onNodeWithText("Today (5 Jan)").performClick()
        compose.onNodeWithContentDescription("Go to Today").performClick()

        assertEquals(listOf(-2, -1, 1, 2), moves)
        assertEquals(1, dateClicks)
        assertEquals(1, todayClicks)
    }

    /* ---------------- EDITOR TOOLBAR ---------------- */

    @Test
    fun `editor toolbar buttons call their actions`() {
        val clicks = mutableListOf<String>()
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                EditorToolbar(
                    enabled = true,
                    onAddType = { clicks += "template" },
                    onFormat = { clicks += "format" },
                    onAddTodoClick = { clicks += "todo" },
                    onAddRecurringClick = { clicks += "recurring" },
                    onManageRecurringClick = { clicks += "manage" }
                )
            }
        }

        // The toolbar scrolls sideways; click through accessibility so off-screen tools work too
        compose.onNodeWithContentDescription("Add Todo").tap()
        compose.onNodeWithContentDescription("Add Recurring Task").tap()
        compose.onNodeWithContentDescription("Add").tap()

        assertEquals(listOf("todo", "recurring", "template"), clicks)
    }

    /* ---------------- APP LOCK SWITCH ---------------- */

    @Test
    fun `lock switch off asks to enable`() {
        var enable = 0
        var disable = 0
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                AppLockToggle(LockMode.OFF, onEnableRequested = { enable++ }, onDisableRequested = { disable++ })
            }
        }

        compose.onNodeWithText("Off").assertExists()
        compose.onNode(isToggleable()).assertIsOff().performClick()
        assertEquals(1 to 0, enable to disable)
    }

    @Test
    fun `lock switch on asks to disable`() {
        var enable = 0
        var disable = 0
        compose.setContent {
            DiaryTheme(darkTheme = true) {
                AppLockToggle(LockMode.BIOMETRIC, onEnableRequested = { enable++ }, onDisableRequested = { disable++ })
            }
        }

        compose.onNodeWithText("Biometric / Face enabled").assertExists()
        compose.onNode(isToggleable()).assertIsOn().performClick()
        assertEquals(0 to 1, enable to disable)
    }

    /* ---------------- PAGES ---------------- */

    @Test
    fun `empty day shows the placeholder`() {
        compose.setContent {
            DiaryTheme(darkTheme = false) { EmptyDayPlaceholder() }
        }
        compose.onNodeWithText("No entry for this day").assertExists()
        compose.onNodeWithText("Tap Edit or start writing your thoughts").assertExists()
    }

    @Test
    fun `diary page shows text and tasks, long-press offers delete`() {
        val task = TodoEntity(id = 1, diaryId = 1, title = "Stretch", date = "2026-01-05", type = TodoType.DAILY)
        val deleted = mutableListOf<TodoEntity>()
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                DiaryPage(
                    text = "Felt good today",
                    modifier = Modifier.fillMaxSize(),
                    ruledLineColor = Color.Gray,
                    ruledLineThickness = 1f,
                    todos = listOf(task),
                    onDeleteTodo = { deleted += it }
                )
            }
        }

        compose.onNodeWithText("Felt good today").assertExists()
        compose.onNodeWithText("Delete").assertDoesNotExist()

        compose.onNodeWithText("Stretch").performTouchInput { longClick() }
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf(task), deleted)
    }

    @Test
    fun `read-only diary page has no delete option`() {
        val task = TodoEntity(id = 1, diaryId = 1, title = "Stretch", date = "2026-01-05", type = TodoType.DAILY, isDone = true)
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                DiaryPage(
                    text = "",
                    modifier = Modifier.fillMaxSize(),
                    ruledLineColor = Color.Gray,
                    ruledLineThickness = 1f,
                    todos = listOf(task),
                    onDeleteTodo = null
                )
            }
        }

        compose.onNodeWithText("Stretch").performTouchInput { longClick() }
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }
}
