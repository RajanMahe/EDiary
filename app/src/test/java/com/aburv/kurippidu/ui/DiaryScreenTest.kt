package com.aburv.kurippidu.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.DiaryScreen
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.ThemeMode
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.testutil.TEST_TIMEOUT_MS
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.eventually
import com.aburv.kurippidu.testutil.tap
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class DiaryScreenTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var data: TestRepository
    private var diaryId = 0
    private var backPresses = 0
    private var screenLockMode by androidx.compose.runtime.mutableStateOf(LockMode.OFF)
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        data = TestRepository()
        diaryId = runBlocking {
            data.dao.insertDiary(DiaryOwnerEntity(title = "Daily")).toInt().also {
                // Open on today, where the editor is shown
                BackupPreferences.setLastOpenedDate(context, it, today.toString())
                BackupPreferences.setThemeMode(context, ThemeMode.LIGHT)
            }
        }
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                DiaryScreen(
                    diaryId = diaryId,
                    onExportStarted = {},
                    onExportFinished = {},
                    onBack = { backPresses++ },
                    lockMode = screenLockMode,
                    themeMode = ThemeMode.LIGHT
                )
            }
        }
        waitFor(hasContentDescription("Add Todo"))
    }

    @After
    fun tearDown() = data.close()

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntil(TEST_TIMEOUT_MS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForGone(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntil(TEST_TIMEOUT_MS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }

    private fun dayLabel(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("d MMM"))

    @Test
    fun `opens on today with the editor`() {
        compose.onNodeWithText("Today (${dayLabel(today)})").assertExists()
        compose.onNodeWithContentDescription("Add Recurring Task").assertExists()
    }

    @Test
    fun `typing is saved automatically`() {
        compose.onNode(hasSetTextAction()).performTextInput("Hello diary")
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(900) // autosave delay

        eventually {
            assertEquals("Hello diary", runBlocking { data.repository.getEntry(diaryId, today) }?.content)
        }
        waitFor(hasText("Saved"))
    }

    @Test
    fun `daily task can be added and deleted from the editor`() {
        compose.onNodeWithContentDescription("Add Todo").tap()
        compose.onNodeWithText("Add Task").assertExists()
        compose.onNodeWithText("Enter your task").performTextInput("Stretch")
        compose.onNodeWithText("OK").tap()

        waitFor(hasText("Stretch"))

        compose.onNodeWithContentDescription("Delete task").tap()
        waitForGone(hasText("Stretch"))
    }

    @Test
    fun `recurring task can be added from the editor`() {
        compose.onNodeWithContentDescription("Add Recurring Task").tap()
        compose.onNodeWithText("New Recurring Task").assertExists()
        compose.onNodeWithText("Task name").performTextInput("Meditate")
        compose.onNodeWithText("Create").tap()

        waitFor(hasText("Meditate"))
        waitFor(hasText("Manage recurring tasks (1)"))

        compose.onNodeWithText("Manage recurring tasks (1)").tap()
        waitFor(hasText("Recurring Tasks"))
    }

    @Test
    fun `template sheet inserts a section`() {
        compose.onNodeWithContentDescription("Add").tap()
        waitFor(hasText("Workout"))
        compose.onNodeWithText("Workout").tap()
        waitFor(hasText("🏋️ Workout:", substring = true))
    }

    @Test
    fun `going to yesterday shows read-only view with an Edit button`() {
        compose.onNodeWithText("<").tap()
        val yesterday = today.minusDays(1)
        waitFor(hasText(dayLabel(yesterday)))
        waitFor(hasText("No entry for this day"))

        compose.onNodeWithText("Edit").tap()
        waitFor(hasContentDescription("Add Todo"))

        // Back to today with the calendar icon
        compose.onNodeWithContentDescription("Go to Today").tap()
        waitFor(hasText("Today (${dayLabel(today)})"))
    }

    @Test
    fun `date text opens the calendar and a day can be picked`() {
        compose.onNodeWithText("Today (${dayLabel(today)})").tap()
        val monthTitle = today.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        waitFor(hasText(monthTitle))
    }

    @Test
    fun `search icon opens search`() {
        compose.onNodeWithContentDescription("Search").tap()
        waitFor(hasText("Search by date or content…"))
        compose.onNodeWithContentDescription("Close").tap()
        waitForGone(hasText("Search by date or content…"))
    }

    @Test
    fun `back arrow calls onBack`() {
        compose.onNodeWithContentDescription("Back").tap()
        assertEquals(1, backPresses)
    }

    @Test
    fun `system Back on Backup screen closes only that screen`() {
        compose.onNodeWithContentDescription("More").tap()
        compose.onNodeWithText("Backup & Restore").tap()
        waitFor(hasText("Export all diaries"))

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        waitForGone(hasText("Export all diaries"))
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithContentDescription("Add Todo").assertExists()
    }

    @Test
    fun `menu switches to dark mode`() {
        compose.onNodeWithContentDescription("More").tap()
        compose.onNodeWithText("Switch to Dark Mode").tap()
        eventually {
            assertEquals(ThemeMode.DARK, runBlocking { BackupPreferences.themeMode(context).first() })
        }
    }

    @Test
    fun `menu turns the app lock off`() {
        runBlocking { BackupPreferences.setLockMode(context, LockMode.BIOMETRIC) }
        screenLockMode = LockMode.BIOMETRIC
        compose.onNodeWithContentDescription("More").tap()
        compose.onNodeWithText("Biometric / Face enabled").assertExists()
        compose.onNodeWithText("App Lock").assertExists()
        compose.onNode(androidx.compose.ui.test.isToggleable()).tap()
        eventually {
            assertEquals(LockMode.OFF, runBlocking { BackupPreferences.lockMode(context).first() })
        }
    }
}
