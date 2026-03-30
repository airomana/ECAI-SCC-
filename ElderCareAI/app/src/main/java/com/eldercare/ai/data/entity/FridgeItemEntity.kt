package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 冰箱食材实体
 * 用于拍冰箱：识别食材、记录放入时间、根据保质期推算过期日
 */
@Entity(tableName = "fridge_item")
data class FridgeItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 食材名称：青菜、鸡蛋、牛奶等 */
    val name: String,

    /** 分类：蔬菜、肉类、蛋奶等，用于保质期规则 */
    val category: String = "其他",

    /** 放入/首次拍摄时间（时间戳） */
    val addedAt: Long,

    /** 过期日期（时间戳），由 addedAt + 保质期规则 计算或识别时填入 */
    val expiryAt: Long
)
