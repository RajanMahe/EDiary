package com.aburv.kurippidu.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.aburv.kurippidu.testutil.tap
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.aburv.kurippidu.HomeScreen
import com.aburv.kurippidu.LockMode
import com.aburv.kurippidu.ThemeMode
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.observeDiariesWithCountsOnce
import com.aburv.kurippidu.ui.theme.DiaryTheme
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private lateinit var data: TestRepository
    private val opened = mutableListOf<Int>()

    @Before
    fun setUp() {
        data = TestRepository()
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                HomeScreen(
                    onDiarySelected = { opened += it },
                    lockMode = LockMode.OFF,
                    themeMode = ThemeMode.LIGHT
                )
            }
        }
    }

    @After
    fun tearDown() = data.close()

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForNoText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isEmpty() }

    @Test
    fun `empty state invites you to create a diary`() {
        waitForText("No diaries yet")
        compose.onNodeWithText("My Diaries").assertExists()
        compose.onNodeWithText("Tap + to create your first diary").assertExists()
    }

    @Test
    fun `create, open, rename and delete a diary`() {
        waitForText("No diaries yet")

        // Create
        compose.onNodeWithContentDescription("Create diary").tap()
        compose.onNodeWithText("Diary name").performTextInput("Workout")
        compose.onNodeWithText("Create").tap()
        waitForText("Workout")
        compose.onNodeWithText("0 entries", substring = true).assertExists()

        // Open
        compose.onNodeWithText("Workout").tap()
        val id = runBlocking { data.dao.observeDiariesWithCountsOnce() }
            .single().diary.diaryId
        assertEquals(listOf(id), opened)

        // Count updates live after writing an entry
        runBlocking { data.repository.saveEntry(id, LocalDate.of(2026, 1, 5), "Leg day") }
        waitForText("1 entries · Created", substring = true)

        // Rename
        compose.onNodeWithContentDescription("Options").tap()
        compose.onNodeWithText("Rename").tap()
        compose.onNodeWithText("New name").performTextClearance()
        compose.onNodeWithText("New name").performTextInput("Gym")
        compose.onNodeWithText("Rename").tap()
        waitForText("Gym")

        // Delete
        compose.onNodeWithContentDescription("Options").tap()
        compose.onNodeWithText("Delete").tap()
        compose.onNodeWithText("Delete diary").assertExists()
        compose.onNodeWithText("Delete").tap()
        waitForText("No diaries yet")
        waitForNoText("Gym")
    }

    @Test
    fun `cancel leaves things unchanged`() {
        waitForText("No diaries yet")
        compose.onNodeWithContentDescription("Create diary").tap()
        compose.onNodeWithText("Diary name").performTextInput("Food")
        compose.onNodeWithText("Cancel").tap()
        compose.onNodeWithText("New diary").assertDoesNotExist()
        compose.onNodeWithText("No diaries yet").assertExists()
    }
}
