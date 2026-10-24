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
import com.aburv.kurippidu.testutil.TEST_TIMEOUT_MS
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.eventually
import com.aburv.kurippidu.testutil.observeDiariesWithCountsOnce
import com.aburv.kurippidu.testutil.tap
import com.aburv.kurippidu.ui.theme.DiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.aburv.kurippidu.AutoBackupFrequency
import com.aburv.kurippidu.data.backup.BackupCodec
import com.aburv.kurippidu.data.backup.BackupDiary
import com.aburv.kurippidu.data.backup.BackupEntry
import com.aburv.kurippidu.data.backup.FullBackup
import com.aburv.kurippidu.data.backup.ParsedBackup
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
        runBlocking {
            BackupPreferences.setAutoBackupFolder(context, null)
            BackupPreferences.setAutoBackupFrequency(context, AutoBackupFrequency.OFF)
        }
        viewModel = data.viewModel()
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
        compose.waitUntil(TEST_TIMEOUT_MS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun nextStartedIntent(): Intent {
        var intent: Intent? = null
        compose.waitUntil(TEST_TIMEOUT_MS) {
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
        compose.onNodeWithText("Full backup").assertExists()
        compose.onNodeWithText("Automatic backup").assertExists()
        compose.onNodeWithText("This diary").assertExists()
        compose.onNodeWithText("Total entries").assertExists()
        waitFor(hasText("2"))
    }

    @Test
    fun `export all diaries writes a full backup to the file the user picked`() {
        compose.onNodeWithText("Export all diaries").tap()

        val request = nextStartedIntent()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
        assertEquals("application/zip", request.type)
        assertTrue(request.getStringExtra(Intent.EXTRA_TITLE)!!.startsWith("kurippidu_backup_"))
        assertEquals(1, exportStarted)

        val file = File.createTempFile("backup", ".zip")
        pickerReturns(request, file)

        waitFor(hasText("Backup saved"))
        val json = com.aburv.kurippidu.backup.BackupArchive.read(file.readBytes(), File(file.parentFile, "unpacked"))
        val saved = (BackupCodec.decode(json) as ParsedBackup.Full).backup
        assertEquals(3, saved.version)
        assertEquals(listOf("First", "Second"), saved.diaries.single().entries.map { it.content })
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
        compose.onNodeWithText("Export all diaries").tap()
        val request = nextStartedIntent()
        compose.runOnUiThread { shadowOf(compose.activity).receiveResult(request, Activity.RESULT_CANCELED, null) }
        eventually { assertEquals(1, exportFinished) }
    }

    @Test
    fun `old single-diary backup restores into this diary`() {
        val file = File.createTempFile("restore", ".json").apply {
            writeText("""[{"date":"2026-02-01","content":"From backup"}]""")
        }

        compose.onNodeWithText("Restore from backup").tap()
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
    fun `full backup restores other diaries too`() {
        val backup = FullBackup(
            exportedAt = "2026-01-10T10:00:00",
            diaries = listOf(
                BackupDiary(title = "Food", entries = listOf(BackupEntry("2026-03-01", "Dosa")))
            )
        )
        val file = File.createTempFile("restore", ".json").apply { writeText(BackupCodec.encode(backup)) }

        compose.onNodeWithText("Restore from backup").tap()
        pickerReturns(nextStartedIntent(), file)

        waitFor(hasText("1 diary, 1 entry and 0 tasks", substring = true))
        compose.onNodeWithText("Restore").tap()

        waitFor(hasText("Restored 1 diary and 1 entry"))
        val food = runBlocking { data.dao.getDiaryByTitle("Food") }!!
        assertEquals("Dosa", runBlocking { data.repository.getEntry(food.diaryId, LocalDate.of(2026, 3, 1)) }?.content)
    }

    @Test
    fun `a damaged backup is refused with a reason`() {
        val file = File.createTempFile("restore", ".json").apply {
            writeText("""{"app":"kurippidu","version":99,"exportedAt":"x"}""")
        }
        compose.onNodeWithText("Restore from backup").tap()
        pickerReturns(nextStartedIntent(), file)
        waitFor(hasText("newer version", substring = true))
    }

    @Test
    fun `automatic backup - pick a folder, it turns on daily, back up now`() {
        compose.onNodeWithText("Not chosen. Tap to pick a folder.").assertExists()
        compose.onNodeWithText("Backup folder").tap()

        val request = nextStartedIntent()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, request.action)
        val dir = java.nio.file.Files.createTempDirectory("autobackup").toFile()
        pickerReturns(request, dir)

        waitFor(hasText(dir.name))
        waitFor(hasText("Every day"))

        compose.onNodeWithText("Back up now").tap()
        waitFor(hasText("Saved kurippidu_auto_", substring = true))
        assertTrue(dir.list()!!.any { it.startsWith("kurippidu_auto_") })

        // Change to weekly
        compose.onNodeWithText("How often").tap()
        compose.onNodeWithText("Every week").tap()
        waitFor(hasText("Every week"))
        assertEquals(
            AutoBackupFrequency.WEEKLY,
            runBlocking { BackupPreferences.autoBackupFrequency(context).first() }
        )
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
