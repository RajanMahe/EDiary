package com.aburv.kurippidu.ui

import android.app.Application
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.aburv.kurippidu.testutil.TEST_TIMEOUT_MS
import com.aburv.kurippidu.testutil.tap
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.DiaryViewModel
import com.aburv.kurippidu.RecurringTaskManagerSheet
import com.aburv.kurippidu.SearchSheet
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.eventually
import com.aburv.kurippidu.testutil.observeDiariesWithCountsOnce
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class SheetsTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private lateinit var data: TestRepository
    private lateinit var viewModel: DiaryViewModel
    private var diaryId = 0
    private val day = LocalDate.of(2026, 1, 5)

    @Before
    fun setUp() {
        data = TestRepository()
        viewModel = data.viewModel()
        eventually { diaryId = runBlocking { data.dao.observeDiariesWithCountsOnce() }.single().diary.diaryId }
        viewModel.setActiveDiary(diaryId)
    }

    @After
    fun tearDown() = data.close()

    private fun waitForText(text: String, substring: Boolean = false) = compose.waitUntil(TEST_TIMEOUT_MS) {
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    /* ---------------- SEARCH ---------------- */

    @Test
    fun `search shows matches and opens the chosen day`() {
        runBlocking {
            data.repository.saveEntry(diaryId, day, "Leg day at the gym")
            data.repository.saveEntry(diaryId, day.plusDays(1), "Rest day")
        }
        val opened = mutableListOf<LocalDate>()
        var closed = false
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                SearchSheet(onClose = { closed = true }, onResultClick = { opened += it }, viewModel = viewModel)
            }
        }

        compose.onNodeWithText("Search by date or content…").performTextInput("gym")
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(300) // search waits for typing to pause
        waitForText("5 Jan 2026")
        compose.onNodeWithText("Leg day at the gym").assertExists()
        compose.onNodeWithText("Rest day").assertDoesNotExist()

        compose.onNodeWithText("5 Jan 2026").tap()
        assertEquals(listOf(day), opened)

        compose.onNodeWithContentDescription("Close").tap()
        assertEquals(true, closed)
    }

    @Test
    fun `search says when nothing matches`() {
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                SearchSheet(onClose = {}, onResultClick = {}, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("Search by date or content…").performTextInput("zzz")
        compose.onNodeWithText("No results").assertExists()
    }

    /* ---------------- RECURRING TASK MANAGER ---------------- */

    @Test
    fun `manager shows empty state`() {
        compose.setContent {
            DiaryTheme(darkTheme = false) { RecurringTaskManagerSheet(viewModel = viewModel, onDismiss = {}) }
        }
        compose.onNodeWithText("Recurring Tasks").assertExists()
        compose.onNodeWithText("No recurring tasks yet").assertExists()
    }

    @Test
    fun `manager lists, edits and deletes recurring tasks`() {
        viewModel.addRecurringTodo("Gym", day, day.plusDays(2))
        eventually { assertEquals(1, viewModel.recurringTodos.size) }

        compose.setContent {
            DiaryTheme(darkTheme = false) { RecurringTaskManagerSheet(viewModel = viewModel, onDismiss = {}) }
        }

        waitForText("Gym")
        compose.onNodeWithText("5 Jan 2026 → 7 Jan 2026").assertExists()

        // Edit: keep dates, press Save
        compose.onNodeWithContentDescription("Edit dates").tap()
        compose.onNodeWithText("Edit date range").assertExists()
        compose.onNodeWithText("From: 5 Jan 2026").assertExists()
        compose.onNodeWithText("Save").tap()
        compose.onNodeWithText("Edit date range").assertDoesNotExist()

        // Delete with confirmation
        compose.onNodeWithContentDescription("Delete").tap()
        compose.onNodeWithText("Delete task?").assertExists()
        compose.onNodeWithText("\"Gym\" will be removed from all days.").assertExists()
        compose.onNodeWithText("Delete").tap()

        waitForText("No recurring tasks yet")
        eventually { assertEquals(emptyList<Any>(), viewModel.recurringTodos) }
    }

    @Test
    fun `cancelling delete keeps the task`() {
        viewModel.addRecurringTodo("Read", day, day.plusDays(1))
        eventually { assertEquals(1, viewModel.recurringTodos.size) }
        compose.setContent {
            DiaryTheme(darkTheme = false) { RecurringTaskManagerSheet(viewModel = viewModel, onDismiss = {}) }
        }
        waitForText("Read")
        compose.onNodeWithContentDescription("Delete").tap()
        compose.onNodeWithText("Cancel").tap()
        compose.onNodeWithText("Read").assertExists()
        assertEquals(1, viewModel.recurringTodos.size)
    }
}
