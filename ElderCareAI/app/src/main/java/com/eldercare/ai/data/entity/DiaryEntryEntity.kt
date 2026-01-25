package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 语音日记/饮食记录实体
 * 用于「今天吃了啥」、情感关键词分析、子女周报
 */
@Entity(tableName = "diary_entry")
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 记录时间 */
    val date: Long,

    /** 用户语音转文字内容 */
    val content: String,

    /** 情感/情绪标签：开心、孤单、满意等 */
    val emotion: String = "",

    /** AI 回复内容 */
    val aiResponse: String = ""
)
