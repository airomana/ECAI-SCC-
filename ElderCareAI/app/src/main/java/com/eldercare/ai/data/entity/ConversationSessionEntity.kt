package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对话会话实体
 * 每次与老人的完整对话记录为一个 Session，包含多轮消息
 */
@Entity(tableName = "conversation_session")
data class ConversationSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 会话开始时间 */
    val startTime: Long = System.currentTimeMillis(),

    /** 会话结束时间（null 表示进行中） */
    val endTime: Long? = null,

    /** 本次会话综合情绪（由多轮分析汇总） */
    val overallEmotion: String = "平静",

    /** 情绪强度 0-100 */
    val emotionIntensity: Int = 50,

    /** 会话摘要（AI生成） */
    val summary: String = "",

    /** 消息数量 */
    val messageCount: Int = 0
)
