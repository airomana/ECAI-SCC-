package com.eldercare.ai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fridge_scan_item",
    foreignKeys = [
        ForeignKey(
            entity = FridgeScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scanId"]),
        Index(value = ["expiryAt"])
    ]
)
data class FridgeScanItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scanId: Long,
    val name: String,
    val category: String = "其他",
    val addedAt: Long,
    val expiryAt: Long
)
