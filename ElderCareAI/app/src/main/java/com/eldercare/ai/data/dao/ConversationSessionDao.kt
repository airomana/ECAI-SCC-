package com.eldercare.ai.data.dao

import androidx.room.*
import com.eldercare.ai.data.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSessionDao {

    @Insert
    suspend fun insert(session: ConversationSessionEntity): Long

    @Update
    suspend fun update(session: ConversationSessionEntity)

    @Query("SELECT * FROM conversation_session ORDER BY startTime DESC")
    fun getAll(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_session WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConversationSessionEntity?

    @Query("SELECT * FROM conversation_session WHERE startTime >= :from AND startTime <= :to ORDER BY startTime DESC")
    suspend fun getByTimeRange(from: Long, to: Long): List<ConversationSessionEntity>

    @Query("SELECT * FROM conversation_session ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatest(): ConversationSessionEntity?

    @Query("DELETE FROM conversation_session WHERE id = :id")
    suspend fun deleteById(id: Long)
}
