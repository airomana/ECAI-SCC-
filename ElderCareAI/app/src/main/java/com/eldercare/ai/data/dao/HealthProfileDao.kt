package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eldercare.ai.data.entity.HealthProfile
import kotlinx.coroutines.flow.Flow

/**
 * 健康档案 DAO：设置页健康档案、子女端禁忌
 */
@Dao
interface HealthProfileDao {

    @Query("SELECT * FROM health_profile LIMIT 1")
    fun get(): Flow<HealthProfile?>

    @Query("SELECT * FROM health_profile LIMIT 1")
    suspend fun getOnce(): HealthProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: HealthProfile): Long

    @Update
    suspend fun update(profile: HealthProfile)

    @Query("DELETE FROM health_profile")
    suspend fun deleteAll()
}
