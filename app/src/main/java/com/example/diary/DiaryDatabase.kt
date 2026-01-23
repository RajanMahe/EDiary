package com.example.diary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DiaryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class DiaryDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: DiaryDatabase? = null

        fun getDatabase(context: Context): DiaryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiaryDatabase::class.java,
                    "diary_db"
                )
                    // TODO: Replace with proper migrations before release
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
