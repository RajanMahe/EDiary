package com.aburv.kurippidu.ui

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.BackupPreferences
import com.aburv.kurippidu.BackupRestoreScreen
import com.aburv.kurippidu.DiaryViewModel
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.eventually
import com.aburv.kurippidu.testutil.observeDiariesWithCountsOnce
import com.aburv.kurippidu.testutil.tap
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
// Tall screen so the whole list is drawn without scrolling
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class BackupRestoreScreenTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var data: TestRepository
    private lateinit var viewModel: DiaryViewModel
    private var diaryId = 0
    private val day = LocalDate.of(2026, 1, 5)

    private var exportStarted = 0
    private var exportFinished = 0
    private var backPresses = 0

    @Before
    fun setUp() {
        data = TestRepository()
        viewModel = DiaryViewModel(ApplicationProvider.getApplicationContext<Application>())
        eventually { diaryId = runBlocking { data.dao.observeDiariesWithCountsOnce() }.single().diary.diaryId }
        viewModel.setActiveDiary(diaryId)
        runBlocking {
            data.repository.saveEntry(diaryId, day, "First")
            data.repository.saveEntry(diaryId, day.plusDays(1), "Second")
        }
        compose.setContent {
            DiaryTheme(darkTheme = false) {
                BackupRestoreScreen(
                    viewModel = viewModel,
                    onExportStarted = { exportStarted++ },
                    onExportFinished = { exportFinished++ },
                    onBack = { backPresses++ }
                )
            }
        }
    }

    @After
    fun tearDown() = data.close()

    private fun waitFor(matcher: SemanticsMatcher) =
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun nextStartedIntent(): Intent {
        var intent: Intent? = null
        compose.waitUntil(5_000) {
            intent = shadowOf(compose.activity).nextStartedActivityForResult?.intent
            intent != null
        }
        return intent!!
    }

    /** Answers the system file picker as if the user chose [file]. */
    private fun pickerReturns(request: Intent, file: File) {
        compose.runOnUiThread {
            shadowOf(compose.activity).receiveResult(request, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
    }

    @Test
    fun `shows sections and the entry count`() {
        compose.onNodeWithText("Backup & Restore").assertExists()
        compose.onNodeWithText("Export").assertExists()
        compose.onNodeWithText("Import").assertExists()
        compose.onNodeWithText("Total entries").assertExists()
        waitFor(hasText("2"))
    }

    @Test
    fun `export as JSON writes the file the user picked`() {
        compose.onNodeWithText("Export as JSON").tap()

        val request = nextStartedIntent()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
        assertEquals("application/json", request.type)
        assertTrue(request.getStringExtra(Intent.EXTRA_TITLE)!!.startsWith("kurippidu_backup_"))
        assertEquals(1, exportStarted)

        val file = File.createTempFile("backup", ".json")
        pickerReturns(request, file)

        waitFor(hasText("Backup saved"))
        val saved = JSONArray(file.readText())
        assertEquals(2, saved.length())
        assertEquals("First", saved.getJSONObject(0).getString("content"))
        assertEquals(1, exportFinished)
        assertNotNull(runBlocking { BackupPreferences.lastBackupTime(context).first() })
    }

    @Test
    fun `export as Markdown asks for a md file`() {
        compose.onNodeWithText("Export as Markdown").tap()
        val request = nextStartedIntent()
        assertEquals("text/markdown", request.type)

        val file = File.createTempFile("backup", ".md")
        pickerReturns(request, file)

        waitFor(hasText("Backup saved"))
        assertTrue(file.readText().contains("## 2026-01-05\n\nFirst"))
    }

    @Test
    fun `cancelling the save picker re-enables the lock and writes nothing`() {
        compose.onNodeWithText("Export as JSON").tap()
        val request = nextStartedIntent()
        compose.runOnUiThread { shadowOf(compose.activity).receiveResult(request, Activity.RESULT_CANCELED, null) }
        eventually { assertEquals(1, exportFinished) }
    }

    @Test
    fun `restore from a JSON file after confirming`() {
        val file = File.createTempFile("restore", ".json").apply {
            writeText("""[{"date":"2026-02-01","content":"From backup"}]""")
        }

        compose.onNodeWithText("Restore from JSON").tap()
        val request = nextStartedIntent()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)
        pickerReturns(request, file)

        waitFor(hasText("Restore backup"))
        compose.onNodeWithText("Restore").tap()

        eventually {
            assertEquals("From backup", runBlocking { data.repository.getEntry(diaryId, LocalDate.of(2026, 2, 1)) }?.content)
        }
        // Merge: existing entries are kept
        assertEquals("First", runBlocking { data.repository.getEntry(diaryId, day) }?.content)
    }

    @Test
    fun `import from a text file shows a preview first`() {
        val file = File.createTempFile("import", ".txt").apply {
            writeText("2026-03-01\nMarch one\n\n2026-03-02\nMarch two")
        }

        compose.onNodeWithText("Import from TXT").tap()
        val request = nextStartedIntent()
        pickerReturns(request, file)

        waitFor(hasText("Detected 2 entries"))
        compose.onNode(hasText("Import") and hasClickAction()).tap()

        eventually {
            assertEquals("March two", runBlocking { data.repository.getEntry(diaryId, LocalDate.of(2026, 3, 2)) }?.content)
        }
    }

    @Test
    fun `import from clipboard`() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("diary", "5 May 2026\nFrom the clipboard"))

        compose.onNodeWithText("Import from Clipboard").tap()
        waitFor(hasText("Detected 1 entries"))
        compose.onNode(hasText("Import") and hasClickAction()).tap()

        eventually {
            assertEquals("From the clipboard", runBlocking { data.repository.getEntry(diaryId, LocalDate.of(2026, 5, 5)) }?.content)
        }
    }

    @Test
    fun `empty clipboard shows a message`() {
        context.getSystemService(ClipboardManager::class.java).clearPrimaryClip()
        compose.onNodeWithText("Import from Clipboard").tap()
        waitFor(hasText("Clipboard empty"))
    }

    @Test
    fun `system Back closes the screen`() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backPresses)
    }
}
