package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eldercare.ai.data.entity.FridgeItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 冰箱食材 DAO
 */
@Dao
interface FridgeItemDao {

    @Query("SELECT * FROM fridge_item ORDER BY expiryAt ASC")
    fun getAll(): Flow<List<FridgeItemEntity>>

    @Insert
    suspend fun insert(item: FridgeItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<FridgeItemEntity>)

    @Query("DELETE FROM fridge_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM fridge_item")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM fridge_item")
    suspend fun getCount(): Int
}
