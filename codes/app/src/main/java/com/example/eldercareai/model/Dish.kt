package com.example.eldercareai.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 菜品实体类
 * 用于存储识别出的菜品信息
 */
@Entity(tableName = "dishes")
data class Dish(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 菜品名称
    val name: String,
    
    // 菜品描述（大白话翻译）
    val description: String,
    
    // 营养成分
    val nutrition: String,
    
    // 适宜人群
    val suitableFor: String,
    
    // 不适宜人群
    val notSuitableFor: String,
    
    // 图片路径
    val imagePath: String? = null,
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis()
)
