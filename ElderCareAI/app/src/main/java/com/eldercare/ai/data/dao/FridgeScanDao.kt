package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eldercare.ai.data.entity.FridgeScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeScanDao {
    @Query("SELECT * FROM fridge_scan ORDER BY scannedAt DESC")
    fun getAll(): Flow<List<FridgeScanEntity>>

    @Query("SELECT * FROM fridge_scan WHERE id = :scanId LIMIT 1")
    fun getById(scanId: Long): Flow<FridgeScanEntity?>

    @Insert
    suspend fun insert(scan: FridgeScanEntity): Long

    @Query("DELETE FROM fridge_scan WHERE id = :scanId")
    suspend fun deleteById(scanId: Long)

    @Query("DELETE FROM fridge_scan")
    suspend fun deleteAll()
}
