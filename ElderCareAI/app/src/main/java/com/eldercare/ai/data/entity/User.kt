package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户实体
 * 存储用户基本信息：手机号、角色、邀请码等
 */
@Entity(
    tableName = "user",
    indices = [Index(value = ["phone"], unique = true)]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 手机号（唯一） */
    val phone: String,

    /** 用户角色：parent（父母端）或 child（子女端） */
    val role: String,

    /** 邀请码（父母端生成，子女端使用） */
    val inviteCode: String? = null,

    /** 关联的家庭ID（通过邀请码关联） */
    val familyId: String? = null,

    /** 昵称 */
    val nickname: String? = null,

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最后登录时间 */
    val lastLoginAt: Long = System.currentTimeMillis()
)
