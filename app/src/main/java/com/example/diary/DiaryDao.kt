package com.example.diary.data

import androidx.room.*

@Dao
interface DiaryDao {

    @Query("SELECT * FROM diary WHERE date = :date ORDER BY id ASC")
    suspend fun getByDate(date: String): List<DiaryEntity>

    @Query("SELECT * FROM diary ORDER BY id ASC")
    suspend fun getAll(): List<DiaryEntity>



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diary: DiaryEntity)

    @Delete
    suspend fun delete(diary: DiaryEntity)


    @Query("SELECT * FROM diary WHERE date = :date")
    suspend fun getDiaryByDate(date: String): DiaryEntity?

    @Query("""
    SELECT * FROM diary 
    WHERE content LIKE :query 
    OR date LIKE :query
    ORDER BY date DESC
            """)
    suspend fun searchDiary(query: String): List<DiaryEntity>


    @Query("SELECT * FROM diary ORDER BY date ASC")
    suspend fun getAllForExport(): List<DiaryEntity>

    @Query("DELETE FROM diary")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM diary")
    suspend fun getCount(): Int






}
