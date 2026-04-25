package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_situation")
data class PersonalSituationEntity(
    @PrimaryKey
    val id: Long = 1,
    val city: String = "",
    val livingAlone: Boolean = false,
    val tastePreferences: List<String> = emptyList(),
    val chewLevel: String = "",
    val preferSoftFood: Boolean = false,
    val symptoms: List<String> = emptyList(),
    val bloodPressureStatus: String = "",
    val bloodSugarStatus: String = "",
    val shareHealth: Boolean = false,
    val shareDiet: Boolean = false,
    val shareContacts: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
