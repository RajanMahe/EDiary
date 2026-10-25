package com.aburv.kurippidu.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import com.aburv.kurippidu.ProgressScreen
import com.aburv.kurippidu.data.DiaryOwnerEntity
import com.aburv.kurippidu.data.DiaryType
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TEST_TIMEOUT_MS
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.tap
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressScreenTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val data = TestRepository()
    private val today = LocalDate.of(2026, 2, 10)
    private var backs = 0

    @After
    fun tearDown() = data.close()

    private fun show(type: DiaryType, entries: Map<Long, String>) {
        val id = runBlocking {
            data.dao.insertDiary(DiaryOwnerEntity(title = "Log", type = type.name)).toInt().also { id ->
                entries.forEach { (daysAgo, text) -> data.repository.saveEntry(id, today.minusDays(daysAgo), text) }
            }
        }
        compose.setContent {
            DiaryTheme(darkTheme = false) { ProgressScreen(diaryId = id, onBack = { backs++ }, today = today) }
        }
    }

    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(TEST_TIMEOUT_MS) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollTo(text: String) =
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = true))

    @Test
    fun `rehab log shows trend, balance and bests`() {
        show(
            DiaryType.REHAB,
            mapOf(
                9L to "Quad using machine\n15 - 15\n20 - 15\nLeft ,right leg calf rise - 20,26",
                2L to "Quad using machine\n20 - 15\n25 - 12\nLeft ,right leg calf rise - 24,26"
            )
        )
        waitFor("Progress · Log")
        waitFor("2 days are not reviewed yet", substring = true)
        compose.onNodeWithText("Heaviest weight · Quad using machine").assertExists()
        compose.onNodeWithText("Up 25% since 1 Feb").assertExists()

        scrollTo("Left is 8% behind")
        compose.onNodeWithText("Gap was 23% at the start: closing ✓").assertExists()
        scrollTo("Personal bests")
        scrollTo("25 kg · 8 Feb")

        compose.onNodeWithContentDescription("Back").tap()
        assertEquals(1, backs)
    }

    @Test
    fun `food diary shows estimates`() {
        show(DiaryType.FOOD, mapOf(1L to "3 idli, sambar\n2L water\n2 vada", 0L to "1 L water\n2 idli"))
        waitFor("Last 4 weeks · 2 days logged")
        compose.onNodeWithText("1.5 L").assertExists()
        compose.onNodeWithText("Estimates from typical Indian food values", substring = true).assertExists()
        scrollTo("Most eaten")
        scrollTo("Idli")
    }

    @Test
    fun `plain diary has both tabs, and empty states explain what to write`() {
        show(DiaryType.PLAIN, emptyMap())
        waitFor("No workouts yet")
        compose.onNodeWithText("Food").tap()
        waitFor("No food logged yet")
    }
}
