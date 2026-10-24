package com.aburv.kurippidu.testutil

import android.content.Context
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.data.DiaryDao
import com.aburv.kurippidu.data.DiaryDatabase
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.data.DiaryWithCount
import kotlinx.coroutines.flow.first

/**
 * How long tests wait for something to appear. Generous because CI machines
 * are much slower than a laptop, and the first test in each class also starts
 * Robolectric and Compose. Passing tests return as soon as the condition holds.
 */
const val TEST_TIMEOUT_MS = 20_000L

/** Fresh in-memory database using Room's normal background threads. */
fun createTestDatabase(
    context: Context = ApplicationProvider.getApplicationContext()
): DiaryDatabase =
    Room.inMemoryDatabaseBuilder(context, DiaryDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/**
 * Creates a fresh in-memory database and makes DiaryRepository.get() return it,
 * so ViewModels and screens built by the app's normal code use it.
 */
class TestRepository {
    val db: DiaryDatabase = createTestDatabase()
    val repository = DiaryRepository(db)
    val dao get() = db.diaryDao()

    /** ViewModels made by the test; cleared in [close] so nothing keeps running. */
    val viewModels = androidx.lifecycle.ViewModelStore()

    init {
        DiaryRepository.setInstanceForTests(repository)
        // The app starts WorkManager automatically; Robolectric tests must do it themselves
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext()
        )
    }

    /** Creates a ViewModel the way the app does, tied to this test. */
    inline fun <reified T : androidx.lifecycle.ViewModel> viewModel(): T =
        androidx.lifecycle.ViewModelProvider(
            viewModels,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(
                ApplicationProvider.getApplicationContext<android.app.Application>()
            )
        )[T::class.java]

    /**
     * Stops the test's ViewModels. The in-memory database is left for the
     * garbage collector rather than closed, because a ViewModel's last save
     * may still be finishing on a background thread.
     */
    fun close() {
        viewModels.clear()
        DiaryRepository.setInstanceForTests(null)
    }
}

/** Current diaries with entry counts (first value of the live query). */
suspend fun DiaryDao.observeDiariesWithCountsOnce(): List<DiaryWithCount> =
    observeDiariesWithCounts().first()

/**
 * Retries [assertion] until it passes or [timeoutMs] runs out. Room and the
 * ViewModels update on background threads, so results arrive a moment later.
 */
fun eventually(timeoutMs: Long = TEST_TIMEOUT_MS, assertion: () -> Unit) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        try {
            assertion()
            return
        } catch (e: Throwable) {
            if (System.currentTimeMillis() > deadline) throw e
            Thread.sleep(10)
        }
    }
}

/**
 * Clicks through the accessibility action instead of a simulated touch.
 * On the JVM, touches can miss bottom sheets and pop-ups while they animate in.
 */
fun androidx.compose.ui.test.SemanticsNodeInteraction.tap(): androidx.compose.ui.test.SemanticsNodeInteraction =
    performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
