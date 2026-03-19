package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 情绪日志实体（按天汇总）
 * 每天结束后自动生成一条情绪日志
 */
@Entity(tableName = "emotion_log")
data class EmotionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 日期（yyyy-MM-dd 格式的时间戳，取当天零点） */
    val dayTimestamp: Long,

    /** 当天主要情绪 */
    val dominantEmotion: String,

    /** 当天情绪分布 JSON，如 {"开心":3,"平静":2,"孤单":1} */
    val emotionDistributionJson: String = "{}",

    /** 当天对话次数 */
    val conversationCount: Int = 0,

    /** 当天总消息数 */
    val totalMessages: Int = 0,

    /** 当天情绪摘要（AI生成或本地生成） */
    val summary: String = "",

    /** 是否已发送给子女 */
    val sentToFamily: Boolean = false,

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis()
)
