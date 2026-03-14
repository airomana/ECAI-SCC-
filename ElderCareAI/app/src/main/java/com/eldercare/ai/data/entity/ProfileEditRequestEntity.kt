package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_edit_request")
data class ProfileEditRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val status: String = "pending",
    val proposerUserId: Long = 0,
    val proposerRole: String = "",
    val payloadJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val handledAt: Long = 0
)
