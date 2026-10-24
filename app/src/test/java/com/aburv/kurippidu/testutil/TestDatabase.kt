package com.aburv.kurippidu.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aburv.kurippidu.data.DiaryDao
import com.aburv.kurippidu.data.DiaryDatabase
import com.aburv.kurippidu.data.DiaryRepository
import com.aburv.kurippidu.data.DiaryWithCount
import kotlinx.coroutines.flow.first

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

    init {
        DiaryRepository.setInstanceForTests(repository)
    }

    fun close() {
        DiaryRepository.setInstanceForTests(null)
        db.close()
    }
}

/** Current diaries with entry counts (first value of the live query). */
suspend fun DiaryDao.observeDiariesWithCountsOnce(): List<DiaryWithCount> =
    observeDiariesWithCounts().first()

/**
 * Retries [assertion] until it passes or [timeoutMs] runs out. Room and the
 * ViewModels update on background threads, so results arrive a moment later.
 */
fun eventually(timeoutMs: Long = 5_000, assertion: () -> Unit) {
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
