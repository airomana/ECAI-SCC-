package com.eldercare.ai.data.dao

import androidx.room.*
import com.eldercare.ai.data.entity.ConversationMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationMessageDao {

    @Insert
    suspend fun insert(message: ConversationMessageEntity): Long

    @Query("SELECT * FROM conversation_message WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: Long): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_message WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionSync(sessionId: Long): List<ConversationMessageEntity>

    @Query("SELECT * FROM conversation_message WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    suspend fun getByTimeRange(from: Long, to: Long): List<ConversationMessageEntity>

    @Query("SELECT COUNT(*) FROM conversation_message WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    @Query("DELETE FROM conversation_message WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
