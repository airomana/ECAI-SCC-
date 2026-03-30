package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_link_request")
data class FamilyLinkRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val parentUserId: Long = 0,
    val childUserId: Long = 0,
    val status: String = "pending",
    val createdAt: Long = System.currentTimeMillis(),
    val handledAt: Long = 0
)
