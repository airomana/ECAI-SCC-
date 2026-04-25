package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contact")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val phone: String = "",
    val relation: String = "",
    val isPrimary: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
