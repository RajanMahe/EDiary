package com.aburv.kurippidu.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aburv.kurippidu.data.security.DatabaseEncryption
import com.aburv.kurippidu.data.security.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        DiaryOwnerEntity::class,
        DiaryEntity::class,
        TodoEntity::class
    ],
    version = 8,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS todo (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                diaryId INTEGER NOT NULL,
                title TEXT NOT NULL,
                date TEXT NOT NULL,
                type TEXT NOT NULL,
                startDate TEXT,
                endDate TEXT,
                time INTEGER,
                isDone INTEGER NOT NULL DEFAULT 0
            )
        """)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // add any v6 schema changes here, or leave empty if only entity list changed
            }
        }

        /**
         * 🔹 Migration 6 → 7
         * Links each day's recurring-task check (DAILY_OVERRIDE) to its parent
         * task by id instead of by title.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE todo ADD COLUMN parentId INTEGER")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_todo_parentId ON todo (parentId)"
                )
                // Backfill: match existing overrides to their recurring task by title
                database.execSQL("""
                    UPDATE todo SET parentId = (
                        SELECT r.id FROM todo r
                        WHERE r.type = 'RECURRING'
                          AND r.diaryId = todo.diaryId
                          AND r.title = todo.title
                        ORDER BY r.id
                        LIMIT 1
                    )
                    WHERE type = 'DAILY_OVERRIDE'
                """)
            }
        }

        /**
         * 🔹 Migration 7 → 8
         * Remembers when each entry was last edited ("Last edited 9:32 PM").
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE entries ADD COLUMN updatedAt INTEGER")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
        )

        const val NAME = "diary_db"

        fun getDatabase(context: Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): DiaryDatabase {
            val builder = Room.databaseBuilder(context, DiaryDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                // Only very old v1 databases are reset (no 1 → 2 migration exists).
                // Any other missing migration fails loudly instead of wiping user data.
                .fallbackToDestructiveMigrationFrom(1)

            // Encrypt with SQLCipher. If this phone can't (or the one-time
            // conversion fails), keep using the plain database and retry next launch.
            val encrypted = runCatching {
                System.loadLibrary("sqlcipher")
                val passphrase = DatabaseKey.getOrCreate(context)
                DatabaseEncryption.setAsideIfUnreadable(context, NAME, passphrase)
                if (DatabaseEncryption.encryptIfNeeded(context, NAME, passphrase)) passphrase else null
            }.getOrNull()

            if (encrypted == null) {
                // Can't use encryption this time. An already-encrypted file can't be
                // opened without its key, so move it aside instead of crashing.
                val file = context.getDatabasePath(NAME)
                if (DatabaseEncryption.stateOf(file) == DatabaseEncryption.State.ENCRYPTED) {
                    file.renameTo(java.io.File("${file.path}.unreadable-${System.currentTimeMillis()}"))
                }
            } else {
                builder.openHelperFactory(SupportOpenHelperFactory(encrypted.toByteArray(Charsets.UTF_8)))
            }
            return builder.build()
        }
    }
}
