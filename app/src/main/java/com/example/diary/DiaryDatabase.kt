package com.example.diary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DiaryOwnerEntity::class,
        DiaryEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class DiaryDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao

    companion object {

        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        /**
         * 🔹 Migration 2 → 3
         * Introduces multi-diary structure
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // 1️⃣ Create diaries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS diaries (
                        diaryId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)

                // 2️⃣ Insert default diary
                database.execSQL("""
                    INSERT INTO diaries (diaryId, title, createdAt)
                    VALUES (1, 'My Diary', ${System.currentTimeMillis()})
                """)

                // 3️⃣ Rename old table
                database.execSQL("ALTER TABLE diary RENAME TO entries_old")

                // 4️⃣ Create new entries table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        diaryOwnerId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        content TEXT NOT NULL,
                        FOREIGN KEY(diaryOwnerId) REFERENCES diaries(diaryId)
                        ON DELETE CASCADE
                    )
                """)

                // 5️⃣ Copy old data
                database.execSQL("""
                    INSERT INTO entries (id, diaryOwnerId, date, content)
                    SELECT id, 1, date, content FROM entries_old
                """)

                // 6️⃣ Drop old table
                database.execSQL("DROP TABLE entries_old")

                // 7️⃣ Create UNIQUE index
                database.execSQL("""
                    CREATE UNIQUE INDEX index_entries_diaryOwnerId_date
                    ON entries (diaryOwnerId, date)
                """)
            }
        }

        /**
         * 🔹 Migration 3 → 4
         * Adds missing non-unique index
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_entries_diaryOwnerId
                    ON entries (diaryOwnerId)
                """)
            }
        }

        fun getDatabase(context: Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "diary_db"
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
