package com.eldercare.ai.data.dao

import androidx.room.*
import com.eldercare.ai.data.entity.EmotionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmotionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: EmotionLogEntity): Long

    @Update
    suspend fun update(log: EmotionLogEntity)

    @Query("SELECT * FROM emotion_log ORDER BY dayTimestamp DESC")
    fun getAll(): Flow<List<EmotionLogEntity>>

    @Query("SELECT * FROM emotion_log WHERE dayTimestamp >= :from AND dayTimestamp <= :to ORDER BY dayTimestamp DESC")
    suspend fun getByTimeRange(from: Long, to: Long): List<EmotionLogEntity>

    @Query("SELECT * FROM emotion_log WHERE dayTimestamp = :dayTimestamp LIMIT 1")
    suspend fun getByDay(dayTimestamp: Long): EmotionLogEntity?

    @Query("SELECT * FROM emotion_log ORDER BY dayTimestamp DESC LIMIT 7")
    suspend fun getLatest7Days(): List<EmotionLogEntity>

    @Query("UPDATE emotion_log SET sentToFamily = 1 WHERE id = :id")
    suspend fun markAsSent(id: Long)
}
