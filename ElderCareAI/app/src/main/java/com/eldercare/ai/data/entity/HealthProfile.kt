package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户健康档案实体
 * 用于健康规则匹配、子女远程设置饮食禁忌
 */
@Entity(tableName = "health_profile")
data class HealthProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 姓名 */
    val name: String = "",

    /** 年龄 */
    val age: Int = 0,

    /** 疾病/慢病：高血压、糖尿病等，逗号或列表 */
    val diseases: List<String> = emptyList(),

    /** 过敏/禁忌：不能吃花生、海鲜等 */
    val allergies: List<String> = emptyList(),

    /** 更新时间 */
    val updatedAt: Long = System.currentTimeMillis()
)
