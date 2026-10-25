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
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.onRoot
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
import org.junit.Assert.assertTrue
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
    private var settingsOpened = 0
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
                    onOpenSettings = { settingsOpened++ }
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
    fun `template sheet follows the diary type`() {
        runBlocking { data.repository.setDiaryType(data.dao.getDiaryById(diaryId)!!, com.aburv.kurippidu.data.DiaryType.GYM) }
        compose.onNodeWithContentDescription("Add").tap()
        waitFor(hasText("Single leg / arm"))
        compose.onNodeWithText("Workout").assertDoesNotExist()
        compose.onNodeWithText("Single leg / arm").tap()
        waitFor(hasText("Left … kg … rep … set", substring = true))
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
    fun `menu opens settings`() {
        compose.onNodeWithContentDescription("More").tap()
        compose.onNodeWithText("Settings").tap()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun `typing shows Typing then Saved, a word count and the edit time`() {
        compose.onNode(hasSetTextAction()).performTextInput("Three small words")
        waitFor(hasText("Typing…"))
        waitFor(hasText("3 words", substring = true))

        mainDispatcher.dispatcher.scheduler.advanceTimeBy(900)
        waitFor(hasText("Saved"))
        waitFor(hasText("Last edited", substring = true))
    }

    @Test
    fun `swiping a read-only day turns the page`() {
        runBlocking {
            data.repository.saveEntry(diaryId, today.minusDays(2), "Two days ago")
        }
        compose.onNodeWithText("<").tap()
        waitFor(hasText(dayLabel(today.minusDays(1))))

        // Swipe right = previous day
        // Swipe across the page area (neighbouring days are drawn too, off-screen)
        compose.onRoot().performTouchInput { swipeRight(startX = left + 5f, endX = right - 5f) }
        waitFor(hasText(dayLabel(today.minusDays(2))))
        waitFor(hasText("Two days ago"))
    }

    /* ---------------- PHASE 3 ---------------- */

    @Test
    fun `tapping a mood saves it, tapping again clears it`() {
        compose.onNodeWithContentDescription("Mood Good").tap()
        eventually {
            assertEquals("good", runBlocking { data.repository.getEntry(diaryId, today) }?.mood)
        }
        compose.onNodeWithContentDescription("Mood Good").tap()
        eventually {
            assertEquals(null, runBlocking { data.repository.getEntry(diaryId, today) }?.mood)
        }
    }

    @Test
    fun `on this day shows last year's entry and opens it`() {
        // Set before the screen loads the day
        runBlocking { data.repository.saveEntry(diaryId, today.minusYears(1), "A year ago today") }
        compose.onNodeWithText("<").tap()
        compose.onNodeWithContentDescription("Go to Today").tap()

        waitFor(hasText("1 year ago you wrote", substring = true))
        compose.onNodeWithText("A year ago today").tap()
        waitFor(hasText(dayLabel(today.minusYears(1))))
    }

    @Test
    fun `this week summary opens from the menu`() {
        runBlocking { data.repository.saveEntry(diaryId, today, "one two three") }
        compose.onNodeWithContentDescription("More").tap()
        compose.onNodeWithText("This week").tap()
        waitFor(hasText("Days written"))
        waitFor(hasText("1 / 7"))
        waitFor(hasText("3"))
    }

    @Test
    fun `daily reminder can be switched on from the menu`() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        compose.onNodeWithContentDescription("More").tap()
        compose.onNodeWithText("Daily reminder").tap()
        compose.onNode(androidx.compose.ui.test.isToggleable()).tap()
        compose.onNodeWithText("At 9:00 PM", substring = true).assertExists()
        compose.onNodeWithText("Save").tap()

        eventually {
            assertEquals(
                java.time.LocalTime.of(21, 0),
                runBlocking { BackupPreferences.reminderTime(context, diaryId).first() }
            )
        }
        waitFor(hasText("Reminder set", substring = true))
    }

    /* ---------------- PHASE 4 ---------------- */

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `bold button wraps a word`() {
        compose.onNode(hasSetTextAction()).performTextInput("big")
        compose.onNode(hasSetTextAction()).performTextInputSelection(androidx.compose.ui.text.TextRange(0, 3))
        compose.onNodeWithContentDescription("Bold").tap()
        waitFor(hasText("**big**", substring = true))
    }

    @Test
    fun `checklist button makes a checklist line`() {
        compose.onNode(hasSetTextAction()).performTextInput("Buy milk")
        compose.onNodeWithContentDescription("Checklist").tap()
        waitFor(hasText("☐ Buy milk", substring = true))
    }

    @Test
    fun `checklist can be ticked on a read-only day`() {
        runBlocking { data.repository.saveEntry(diaryId, today.minusDays(1), "☐ Pack bags\nNotes") }
        compose.onNodeWithText("<").tap()
        waitFor(hasText("Pack bags"))
        compose.onNode(androidx.compose.ui.test.isToggleable() and hasAnySibling(hasText("Pack bags"))).tap()
        eventually {
            assertEquals("☑ Pack bags\nNotes", runBlocking { data.repository.getEntry(diaryId, today.minusDays(1)) }?.content)
        }
    }

    @Test
    fun `speaking adds the words`() {
        compose.onNodeWithContentDescription("Speak to write").tap()
        val request = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH, request.intent.action)

        val result = android.content.Intent().putStringArrayListExtra(
            android.speech.RecognizerIntent.EXTRA_RESULTS, arrayListOf("hello from my voice")
        )
        compose.runOnUiThread {
            org.robolectric.Shadows.shadowOf(compose.activity)
                .receiveResult(request.intent, android.app.Activity.RESULT_OK, result)
        }
        waitFor(hasText("hello from my voice", substring = true))
    }

    @Test
    fun `add photo offers gallery and camera`() {
        compose.onNodeWithContentDescription("Add photo").tap()
        waitFor(hasText("Choose from gallery"))
        compose.onNodeWithText("Take a photo").assertExists()
        compose.onNodeWithText("Choose from gallery").tap()
        val request = org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult
        assertTrue(request.intent.action in setOf(android.provider.MediaStore.ACTION_PICK_IMAGES, android.content.Intent.ACTION_OPEN_DOCUMENT, android.content.Intent.ACTION_GET_CONTENT))
    }

    @Test
    fun `search shows tag chips that filter`() {
        runBlocking {
            data.repository.saveEntry(diaryId, today.minusDays(3), "Ran 5k #health")
            data.repository.saveEntry(diaryId, today.minusDays(4), "Cinema")
        }
        compose.onNodeWithContentDescription("Search").tap()
        waitFor(hasText("#health (1)"))
        compose.onNodeWithText("#health (1)").tap()
        waitFor(hasText("Ran 5k #health", substring = true))
        compose.onNodeWithText("Cinema").assertDoesNotExist()
    }
}
