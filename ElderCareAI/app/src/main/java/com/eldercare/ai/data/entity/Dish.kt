package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 菜品知识库实体（方案中的 500 道常见菜）
 * 用于拍菜单时的本地知识库查询、健康规则匹配、大白话描述
 */
@Entity(
    tableName = "dish",
    indices = [Index(value = ["name"], unique = true)]
)
data class Dish(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 菜名，如：红烧肉、地三鲜 */
    val name: String,

    /** 主要成分，如：猪五花肉、冰糖、酱油 */
    val ingredients: List<String>,

    /** 烹饪方式：红烧、清蒸、油炸等 */
    val cookingMethod: String,

    /** 标签：高油、高盐、高糖、高热量等 */
    val tags: List<String>,

    /** 营养素简要，如：热量 500kcal/100g */
    val nutrients: String,

    /** 适合人群 */
    val suitableFor: List<String>,

    /** 不适合人群：高血压、糖尿病等 */
    val notSuitableFor: List<String>,

    /** 大白话描述，给老人听的通俗说明 */
    val plainDescription: String
)
