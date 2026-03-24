package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eldercare.ai.data.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 语音日记/饮食记录 DAO
 */
@Dao
interface DiaryEntryDao {

    @Query("SELECT * FROM diary_entry ORDER BY date DESC")
    fun getAll(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entry WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaryEntryEntity?

    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Query("DELETE FROM diary_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM diary_entry WHERE date >= :from AND date <= :to ORDER BY date DESC")
    suspend fun getByTimeRange(from: Long, to: Long): List<DiaryEntryEntity>

    @Query("SELECT * FROM diary_entry ORDER BY date DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<DiaryEntryEntity>

    @Query("DELETE FROM diary_entry")
    suspend fun deleteAll()
}
