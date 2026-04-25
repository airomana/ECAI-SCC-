package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eldercare.ai.data.entity.FridgeScanItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeScanItemDao {
    @Query("SELECT * FROM fridge_scan_item WHERE scanId = :scanId ORDER BY expiryAt ASC")
    fun getByScanId(scanId: Long): Flow<List<FridgeScanItemEntity>>

    @Insert
    suspend fun insertAll(items: List<FridgeScanItemEntity>)

    @Query("DELETE FROM fridge_scan_item WHERE scanId = :scanId")
    suspend fun deleteByScanId(scanId: Long)
}
