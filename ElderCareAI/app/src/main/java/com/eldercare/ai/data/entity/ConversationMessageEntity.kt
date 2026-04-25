package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 对话消息实体
 * 记录每一轮对话中的单条消息（用户说的 或 AI回复的）
 */
@Entity(
    tableName = "conversation_message",
    foreignKeys = [
        ForeignKey(
            entity = ConversationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属会话ID */
    val sessionId: Long,

    /** 消息时间 */
    val timestamp: Long = System.currentTimeMillis(),

    /** 角色：user / assistant */
    val role: String,

    /** 消息内容 */
    val content: String,

    /** 识别到的情绪（仅 user 消息有效） */
    val emotion: String = "",

    /** 情绪强度 0-100（仅 user 消息有效） */
    val emotionIntensity: Int = 50
)
