package com.aburv.kurippidu.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds an old database file by hand, then opens it with the current Room
 * setup. Room runs the migrations and checks the final schema matches the
 * entities, so any broken or missing migration fails here, not on a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun openWithRoom(): DiaryDatabase =
        Room.databaseBuilder(context, DiaryDatabase::class.java, dbName)
            .addMigrations(*DiaryDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `v2 single-diary database upgrades to latest and keeps entries`() {
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { raw ->
            raw.execSQL(
                "CREATE TABLE diary (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "date TEXT NOT NULL, content TEXT NOT NULL)"
            )
            raw.execSQL("INSERT INTO diary (id, date, content) VALUES (1, '2026-01-01', 'New year')")
            raw.execSQL("INSERT INTO diary (id, date, content) VALUES (2, '2026-01-02', 'Day two')")
            raw.version = 2
        }

        val db = openWithRoom()
        runBlocking {
            val dao = db.diaryDao()
            val diaries = dao.observeDiariesWithCounts().first()
            assertEquals(listOf("My Diary"), diaries.map { it.diary.title })
            assertEquals(2, diaries.single().entryCount)
            assertEquals(
                listOf("New year", "Day two"),
                dao.getAllEntries(diaries.single().diary.diaryId).map { it.content }
            )
        }
        db.close()
    }

    @Test
    fun `v6 recurring checks get their parentId in v7`() {
        // Create the exact v6 schema from the exported schema file
        val schema = JSONObject(
            File("schemas/com.aburv.kurippidu.data.DiaryDatabase/6.json").readText()
        ).getJSONObject("database")

        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }

            raw.execSQL("INSERT INTO diaries (diaryId, title, createdAt) VALUES (1, 'My Diary', 0)")
            raw.execSQL(
                "INSERT INTO todo (id, diaryId, title, date, type, startDate, endDate, isDone) " +
                        "VALUES (10, 1, 'Gym', '2026-01-01', 'RECURRING', '2026-01-01', '2026-01-31', 0)"
            )
            raw.execSQL(
                "INSERT INTO todo (id, diaryId, title, date, type, startDate, endDate, isDone) " +
                        "VALUES (11, 1, 'Gym', '2026-01-05', 'DAILY_OVERRIDE', '2026-01-01', '2026-01-31', 1)"
            )
            raw.execSQL(
                "INSERT INTO todo (id, diaryId, title, date, type, isDone) " +
                        "VALUES (12, 1, 'Read', '2026-01-05', 'DAILY', 0)"
            )
            raw.version = 6
        }

        val db = openWithRoom()
        runBlocking {
            val rows = db.diaryDao().observeTodosOnDate(1, "2026-01-05").first().associateBy { it.id }
            assertEquals(10, rows.getValue(11).parentId)
            assertNull(rows.getValue(12).parentId)
        }
        db.close()
    }

    /** Creates the exact schema of [version] from its exported schema file. */
    private fun createFromSchema(version: Int, fill: (android.database.sqlite.SQLiteDatabase) -> Unit) {
        val schema = JSONObject(
            File("schemas/com.aburv.kurippidu.data.DiaryDatabase/$version.json").readText()
        ).getJSONObject("database")
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            fill(raw)
            raw.version = version
        }
    }

    @Test
    fun `v10 entries get their tags in v11`() {
        createFromSchema(10) { raw ->
            raw.execSQL("INSERT INTO diaries (diaryId, title, createdAt) VALUES (1, 'D', 0)")
            raw.execSQL("INSERT INTO entries (id, diaryOwnerId, date, content) VALUES (1, 1, '2026-01-05', 'Busy #Work day #family')")
            raw.execSQL("INSERT INTO entries (id, diaryOwnerId, date, content) VALUES (2, 1, '2026-01-06', 'No tags')")
        }

        val db = openWithRoom()
        runBlocking {
            val tags = db.diaryDao().observeTags(1).first()
            assertEquals(listOf(TagCount("family", 1), TagCount("work", 1)), tags.sortedBy { it.tag })
        }
        db.close()
    }

    @Test
    fun `v8 database upgrades through mood, photos and tags`() {
        createFromSchema(8) { raw ->
            raw.execSQL("INSERT INTO diaries (diaryId, title, createdAt) VALUES (1, 'D', 0)")
            raw.execSQL("INSERT INTO entries (id, diaryOwnerId, date, content, updatedAt) VALUES (1, 1, '2026-01-05', 'Kept #safe', 123)")
        }
        val db = openWithRoom()
        runBlocking {
            val entry = db.diaryDao().getEntryByDate(1, "2026-01-05")!!
            assertEquals("Kept #safe", entry.content)
            assertEquals(123L, entry.updatedAt)
            assertEquals(null, entry.mood)
            assertEquals(listOf(TagCount("safe", 1)), db.diaryDao().observeTags(1).first())
        }
        db.close()
    }

    @Test
    fun `v11 diaries get a type guessed from their name in v12`() {
        createFromSchema(11) { raw ->
            raw.execSQL("INSERT INTO diaries (diaryId, title, createdAt) VALUES (1, 'Gym log', 0)")
            raw.execSQL("INSERT INTO diaries (diaryId, title, createdAt) VALUES (2, 'ACL Rehab', 0)")
            raw.execSQL("INSERT INTO diaries (diaryId, title, createdAt) VALUES (3, 'Meals', 0)")
            raw.execSQL("INSERT INTO diaries (diaryId, title, createdAt) VALUES (4, 'Thoughts', 0)")
            raw.execSQL("INSERT INTO entries (id, diaryOwnerId, date, content) VALUES (1, 1, '2026-01-05', 'Squats')")
        }
        val db = openWithRoom()
        runBlocking {
            val dao = db.diaryDao()
            assertEquals(
                listOf(DiaryType.GYM, DiaryType.REHAB, DiaryType.FOOD, DiaryType.PLAIN),
                dao.getAllDiaries().sortedBy { it.diaryId }.map { it.diaryType }
            )
            assertEquals("Squats", dao.getEntryByDate(1, "2026-01-05")!!.content)
        }
        db.close()
    }
}
