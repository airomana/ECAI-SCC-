package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fridge_scan")
data class FridgeScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scannedAt: Long,
    val itemCount: Int,
    val note: String = ""
)
