package com.example.eldercareai.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 健康档案实体类
 * 用于存储老人的健康信息
 */
@Entity(tableName = "health_profiles")
data class HealthProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 老人姓名
    val name: String,
    
    // 年龄
    val age: Int,
    
    // 慢性病列表（如：高血压、糖尿病等）
    val chronicDiseases: String,
    
    // 过敏食物列表
    val allergies: String,
    
    // 饮食禁忌
    val dietaryRestrictions: String,
    
    // 备注
    val notes: String? = null,
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 更新时间
    val updatedAt: Long = System.currentTimeMillis()
)
