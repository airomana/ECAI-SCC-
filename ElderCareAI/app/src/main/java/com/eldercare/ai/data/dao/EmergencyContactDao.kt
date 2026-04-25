package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eldercare.ai.data.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {

    @Query("SELECT * FROM emergency_contact ORDER BY isPrimary DESC, updatedAt DESC")
    fun getAll(): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EmergencyContactEntity): Long

    @Update
    suspend fun update(entity: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contact WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE emergency_contact SET isPrimary = 0")
    suspend fun clearPrimary()
}
