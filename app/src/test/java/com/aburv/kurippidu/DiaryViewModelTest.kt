package com.aburv.kurippidu

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.DiaryViewModel.BackupEvent
import com.aburv.kurippidu.DiaryViewModel.RestoreMode
import com.aburv.kurippidu.DiaryViewModel.SaveState
import com.aburv.kurippidu.data.TodoType
import com.aburv.kurippidu.testutil.MainDispatcherRule
import com.aburv.kurippidu.testutil.TestRepository
import com.aburv.kurippidu.testutil.eventually
import com.aburv.kurippidu.testutil.observeDiariesWithCountsOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var data: TestRepository
    private lateinit var viewModel: DiaryViewModel
    private var diaryId = 0

    private val today = LocalDate.now()
    private val day = LocalDate.of(2026, 1, 5)

    /** Moves the ViewModel's virtual clock (autosave and search delays). */
    private fun advanceTimeBy(ms: Long) = mainDispatcher.dispatcher.scheduler.advanceTimeBy(ms)

    private fun savedText(date: LocalDate) = runBlocking { data.repository.getEntry(diaryId, date)?.content }

    private fun save(date: LocalDate, text: String) = runBlocking { data.repository.saveEntry(diaryId, date, text) }

    @Before
    fun setUp() {
        data = TestRepository()
        viewModel = DiaryViewModel(app) // creates the default "My Diary"
        eventually {
            diaryId = runBlocking { data.dao.observeDiariesWithCountsOnce() }.single().diary.diaryId
        }
        viewModel.setActiveDiary(diaryId)
    }

    @After
    fun tearDown() = data.close()

    /* ---------------- SETUP ---------------- */

    @Test
    fun `creates a default diary when none exist`() {
        val diaries = runBlocking { data.dao.observeDiariesWithCountsOnce() }
        assertEquals(listOf("My Diary"), diaries.map { it.diary.title })
        assertTrue(viewModel.isReady)
        assertEquals(diaryId, viewModel.activeDiaryId)
    }

    @Test
    fun `setting the same diary twice does not reload`() {
        eventually { assertEquals("", viewModel.diaryText.text) }
        viewModel.updateDiaryText(TextFieldValue("unsaved"))
        viewModel.setActiveDiary(diaryId)
        Thread.sleep(100)
        assertEquals("unsaved", viewModel.diaryText.text)
    }

    /* ---------------- LOAD + AUTOSAVE ---------------- */

    @Test
    fun `loads the saved text for a date`() {
        save(day, "Hello Jan 5")
        viewModel.loadDiaryForDate(day)
        assertEquals(day, viewModel.currentDate)
        eventually { assertEquals("Hello Jan 5", viewModel.diaryText.text) }

        viewModel.loadDiaryForDate(day.plusDays(1))
        eventually { assertEquals("", viewModel.diaryText.text) }
    }

    @Test
    fun `autosaves 800 ms after typing stops`() {
        viewModel.loadDiaryForDate(day)
        viewModel.updateDiaryText(TextFieldValue("Typing"))
        assertEquals(SaveState.DIRTY, viewModel.saveState)

        advanceTimeBy(799)
        Thread.sleep(100)
        assertNull(savedText(day))

        advanceTimeBy(2)
        eventually { assertEquals("Typing", savedText(day)) }
        eventually { assertEquals(SaveState.SAVED, viewModel.saveState) }
    }

    @Test
    fun `each keystroke restarts the autosave timer`() {
        viewModel.loadDiaryForDate(day)
        viewModel.updateDiaryText(TextFieldValue("a"))
        advanceTimeBy(500)
        viewModel.updateDiaryText(TextFieldValue("ab"))
        advanceTimeBy(500)
        Thread.sleep(100)
        assertNull(savedText(day))

        advanceTimeBy(400)
        eventually { assertEquals("ab", savedText(day)) }
    }

    @Test
    fun `saveCurrentDiary saves straight away`() {
        viewModel.loadDiaryForDate(day)
        viewModel.updateDiaryText(TextFieldValue("Now"))
        viewModel.saveCurrentDiary()
        eventually { assertEquals("Now", savedText(day)) }
        eventually { assertEquals(SaveState.SAVED, viewModel.saveState) }

        // Nothing dirty: stays saved
        viewModel.saveCurrentDiary()
        assertEquals(SaveState.SAVED, viewModel.saveState)
    }

    @Test
    fun `switching date saves to the old date, not the new one`() {
        viewModel.loadDiaryForDate(day)
        eventually { assertEquals("", viewModel.diaryText.text) }
        viewModel.updateDiaryText(TextFieldValue("For Jan 5"))

        // What DiaryScreen does on a date change
        viewModel.saveCurrentDiary()
        viewModel.loadDiaryForDate(day.plusDays(1))

        eventually { assertEquals("For Jan 5", savedText(day)) }
        eventually { assertEquals("", viewModel.diaryText.text) }
        assertNull(savedText(day.plusDays(1)))
    }

    @Test
    fun `fast date switching ends on the last date's text`() {
        save(day, "Day A")
        save(day.plusDays(1), "Day B")
        save(day.plusDays(2), "Day C")

        viewModel.loadDiaryForDate(day)
        viewModel.loadDiaryForDate(day.plusDays(1))
        viewModel.loadDiaryForDate(day.plusDays(2))

        eventually { assertEquals("Day C", viewModel.diaryText.text) }
        Thread.sleep(200) // give any slow earlier load time to (wrongly) overwrite it
        assertEquals("Day C", viewModel.diaryText.text)
        assertEquals(day.plusDays(2), viewModel.currentDate)
    }

    @Test
    fun `unsaved text is saved when the ViewModel is cleared (Back pressed)`() {
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[DiaryViewModel::class.java]
        vm.setActiveDiary(diaryId)
        vm.loadDiaryForDate(day)
        eventually { assertEquals("", vm.diaryText.text) }
        vm.updateDiaryText(TextFieldValue("Typed then Back"))

        store.clear() // calls onCleared(); autosave timer is cancelled

        eventually { assertEquals("Typed then Back", savedText(day)) }
    }

    @Test
    fun `nothing is saved on clear when there are no edits`() {
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[DiaryViewModel::class.java]
        vm.setActiveDiary(diaryId)
        vm.loadDiaryForDate(day)
        store.clear()
        Thread.sleep(200)
        assertNull(savedText(day))
    }

    /* ---------------- EDIT MODE ---------------- */

    @Test
    fun `edit mode is on for today and off for other days until enabled`() {
        viewModel.updateEditModeForDate(today)
        assertTrue(viewModel.isEditMode)

        viewModel.updateEditModeForDate(day)
        assertFalse(viewModel.isEditMode)

        viewModel.enableEditMode()
        assertTrue(viewModel.isEditMode)
    }

    /* ---------------- TASKS ---------------- */

    @Test
    fun `daily task can be added, ticked and deleted`() {
        viewModel.loadTodos(day)
        viewModel.addTodo("Call mum", day)

        eventually { assertEquals(listOf("Call mum"), viewModel.todos!!.map { it.title }) }
        eventually { assertTrue(day in viewModel.entryDates) }

        viewModel.toggleTodo(viewModel.todos!!.single(), day)
        eventually { assertTrue(viewModel.todos!!.single().isDone) }

        viewModel.deleteTodo(viewModel.todos!!.single())
        eventually { assertEquals(emptyList<Any>(), viewModel.todos) }
    }

    @Test
    fun `tasks follow the selected date`() {
        viewModel.addTodo("On Jan 5", day)
        viewModel.addTodo("On Jan 6", day.plusDays(1))

        viewModel.loadTodos(day)
        eventually { assertEquals(listOf("On Jan 5"), viewModel.todos!!.map { it.title }) }

        viewModel.loadTodos(day.plusDays(1))
        eventually { assertEquals(listOf("On Jan 6"), viewModel.todos!!.map { it.title }) }

        // Same date again keeps the list (no reload flash)
        viewModel.loadTodos(day.plusDays(1))
        assertEquals(listOf("On Jan 6"), viewModel.todos!!.map { it.title })
    }

    @Test
    fun `recurring task appears on every day in its range`() {
        viewModel.addRecurringTodo("Gym", day, day.plusDays(2))
        eventually { assertEquals(listOf("Gym"), viewModel.recurringTodos.map { it.title }) }

        listOf(day, day.plusDays(1), day.plusDays(2)).forEach { d ->
            viewModel.loadTodos(d)
            eventually { assertEquals("on $d", listOf("Gym"), viewModel.todos?.map { it.title }) }
        }
        viewModel.loadTodos(day.plusDays(3))
        eventually { assertEquals(emptyList<Any>(), viewModel.todos) }
    }

    @Test
    fun `ticking a recurring task affects only that day`() {
        viewModel.addRecurringTodo("Gym", day, day.plusDays(5))

        viewModel.loadTodos(day)
        eventually { assertEquals(1, viewModel.todos?.size) }
        viewModel.toggleTodo(viewModel.todos!!.single(), day)
        eventually { assertTrue(viewModel.todos!!.single().isDone) }
        assertEquals(TodoType.DAILY_OVERRIDE, viewModel.todos!!.single().type)

        viewModel.loadTodos(day.plusDays(1))
        eventually { assertEquals(false, viewModel.todos?.single()?.isDone) }
    }

    @Test
    fun `recurring task dates can be changed and the task deleted`() {
        viewModel.addRecurringTodo("Gym", day, day.plusDays(1))
        eventually { assertEquals(1, viewModel.recurringTodos.size) }

        viewModel.updateRecurringTodoDates(viewModel.recurringTodos.single(), day.plusDays(10), day.plusDays(12))
        viewModel.loadTodos(day)
        eventually { assertEquals(emptyList<Any>(), viewModel.todos) }
        viewModel.loadTodos(day.plusDays(11))
        eventually { assertEquals(listOf("Gym"), viewModel.todos?.map { it.title }) }

        viewModel.toggleTodo(viewModel.todos!!.single(), day.plusDays(11))
        eventually { assertTrue(viewModel.todos!!.single().isDone) }

        viewModel.deleteRecurringTodo(viewModel.recurringTodos.single())
        eventually { assertEquals(emptyList<Any>(), viewModel.recurringTodos) }
        eventually { assertEquals(emptyList<Any>(), viewModel.todos) }
    }

    /* ---------------- SEARCH ---------------- */

    @Test
    fun `search waits for typing to pause, then shows matches`() {
        save(day, "Leg day at the gym")
        save(day.plusDays(1), "Rest day")

        viewModel.onSearchQueryChanged("gym")
        assertEquals("gym", viewModel.searchQuery)
        Thread.sleep(100)
        assertEquals(emptyList<Any>(), viewModel.searchResults)

        advanceTimeBy(300)
        eventually { assertEquals(listOf("Leg day at the gym"), viewModel.searchResults.map { it.content }) }

        viewModel.onSearchQueryChanged("  ")
        assertEquals(emptyList<Any>(), viewModel.searchResults)

        viewModel.onSearchQueryChanged("day")
        advanceTimeBy(300)
        eventually { assertEquals(2, viewModel.searchResults.size) }

        viewModel.clearSearch()
        assertEquals("", viewModel.searchQuery)
        assertEquals(emptyList<Any>(), viewModel.searchResults)
    }

    /* ---------------- EXPORT ---------------- */

    @Test
    fun `exports entries as JSON and Markdown`() = runBlocking {
        save(day, "First")
        save(day.plusDays(1), "Second")
        assertEquals(2, viewModel.getTotalEntryCount())

        val json = JSONArray(viewModel.exportAsJson())
        assertEquals(2, json.length())
        assertEquals("2026-01-05", json.getJSONObject(0).getString("date"))
        assertEquals("First", json.getJSONObject(0).getString("content"))

        val md = viewModel.exportAsMarkdown()
        assertTrue(md.contains("## 2026-01-05\n\nFirst"))
        assertTrue(md.contains("## 2026-01-06\n\nSecond"))
    }

    /* ---------------- IMPORT ---------------- */

    @Test
    fun `text import previews and saves entries`() {
        val raw = "2026-01-05\nFirst day\n\n2026-01-07\nThird day"

        assertEquals(2, viewModel.previewImportCount(raw))
        assertEquals(listOf(day.plusDays(2), day), viewModel.previewImportDates(raw))

        viewModel.loadDiaryForDate(day)
        runBlocking { viewModel.importFromTextSafe(raw) }

        assertEquals("First day", savedText(day))
        assertEquals("Third day", savedText(day.plusDays(2)))
        // The open page refreshes to show the imported text
        eventually { assertEquals("First day", viewModel.diaryText.text) }
    }

    /* ---------------- RESTORE ---------------- */

    private fun collectEvents(): Pair<MutableList<BackupEvent>, CoroutineScope> {
        val events = Collections.synchronizedList(mutableListOf<BackupEvent>())
        val scope = CoroutineScope(Dispatchers.Unconfined)
        viewModel.backupEvents.onEach { events.add(it) }.launchIn(scope)
        return events to scope
    }

    @Test
    fun `restore merges backup into existing entries`() {
        val (events, scope) = collectEvents()
        save(day, "Old Jan 5")
        save(day.plusDays(3), "Kept Jan 8")

        runBlocking {
            viewModel.restoreFromJson(
                """[{"date":"2026-01-05","content":"Backup Jan 5"},{"date":"2026-01-06","content":"Backup Jan 6"}]""",
                RestoreMode.MERGE
            )
        }

        assertEquals("Backup Jan 5", savedText(day))
        assertEquals("Backup Jan 6", savedText(day.plusDays(1)))
        assertEquals("Kept Jan 8", savedText(day.plusDays(3)))
        eventually { assertEquals(listOf(BackupEvent.Success("Restore complete (2 entries)")), events.toList()) }
        scope.cancel()
    }

    @Test
    fun `restore in overwrite mode replaces all entries`() = runBlocking {
        save(day.plusDays(3), "Removed")

        viewModel.restoreFromJson("""[{"date":"2026-01-05","content":"Only this"}]""", RestoreMode.OVERWRITE)

        assertEquals(listOf("Only this"), data.repository.getAllEntries(diaryId).map { it.content })
    }

    @Test
    fun `broken backup changes nothing`() {
        val (events, scope) = collectEvents()
        save(day, "Safe")

        listOf(
            "not json at all",
            """[{"date":"2026-13-45","content":"bad date"}]""",
            """[{"date":"2026-01-06"}]""",
            """[{"date":"2026-01-06","content":"ok"}, 5]"""
        ).forEach { broken ->
            runBlocking { viewModel.restoreFromJson(broken, RestoreMode.OVERWRITE) }
        }

        assertEquals(listOf("Safe"), runBlocking { data.repository.getAllEntries(diaryId) }.map { it.content })
        eventually { assertEquals(4, events.size) }
        assertTrue(events.all { it == BackupEvent.Error("Invalid backup file") })
        scope.cancel()
    }

    /* ---------------- FORMAT + TEMPLATES ---------------- */

    @Test
    fun `formatting wraps the selection`() {
        viewModel.updateDiaryText(TextFieldValue("make this bold", selection = TextRange(5, 9)))
        viewModel.applyFormat(TextFormat.Bold)
        assertEquals("make **this** bold", viewModel.diaryText.text)

        viewModel.updateDiaryText(TextFieldValue("ab", selection = TextRange(0, 1)))
        viewModel.applyFormat(TextFormat.Italic)
        assertEquals("_a_b", viewModel.diaryText.text)

        viewModel.updateDiaryText(TextFieldValue("x", selection = TextRange(1)))
        viewModel.applyFormat(TextFormat.Underline)
        assertEquals("x____", viewModel.diaryText.text)
        assertEquals(SaveState.DIRTY, viewModel.saveState)
    }

    @Test
    fun `formatting a right-to-left selection does not crash`() {
        viewModel.updateDiaryText(TextFieldValue("hello", selection = TextRange(4, 1)))
        viewModel.applyFormat(TextFormat.Bold)
        assertEquals("h**ell**o", viewModel.diaryText.text)
    }

    @Test
    fun `template is inserted at the cursor`() {
        viewModel.updateDiaryText(TextFieldValue("AB", selection = TextRange(1)))
        viewModel.insertTemplate(DiaryTemplate("Food", "🍔"))
        assertEquals("A\n🍔 Food:\nB", viewModel.diaryText.text)
        assertEquals(1 + "\n🍔 Food:\n".length, viewModel.diaryText.selection.start)
    }

    /* ---------------- PAGES ---------------- */

    @Test
    fun `preloads the previous and next day`() = runBlocking {
        save(day.minusDays(1), "Yesterday")
        save(day.plusDays(1), "Tomorrow")
        viewModel.loadDiaryForDate(day)

        val pages = viewModel.preloadPages(day)

        assertEquals("Yesterday", pages.getValue(-1).text)
        assertEquals(day, pages.getValue(0).date)
        assertEquals("Tomorrow", pages.getValue(1).text)
    }

    @Test
    fun `reinitialize resets an empty diary to today`() {
        viewModel.loadDiaryForDate(day)
        viewModel.reinitializeIfEmpty()
        eventually { assertEquals(today, viewModel.currentDate) }
        assertEquals(SaveState.IDLE, viewModel.saveState)
    }

    @Test
    fun `reinitialize leaves a diary with entries alone`() {
        save(day, "Something")
        viewModel.loadDiaryForDate(day)
        viewModel.reinitializeIfEmpty()
        Thread.sleep(200)
        assertEquals(day, viewModel.currentDate)
    }
}
