package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 家庭关系实体
 * 关联父母和子女
 */
@Entity(
    tableName = "family_relation",
    indices = [
        Index(value = ["familyId"]),
        Index(value = ["parentUserId"]),
        Index(value = ["childUserId"])
    ]
)
data class FamilyRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 家庭ID（唯一标识一个家庭） */
    val familyId: String,

    /** 父母用户ID */
    val parentUserId: Long,

    /** 子女用户ID */
    val childUserId: Long,

    /** 关联时间 */
    val linkedAt: Long = System.currentTimeMillis()
)
