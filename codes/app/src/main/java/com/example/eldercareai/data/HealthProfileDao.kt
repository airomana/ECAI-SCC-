package com.example.eldercareai.data

import androidx.room.*
import com.example.eldercareai.model.HealthProfile
import kotlinx.coroutines.flow.Flow

/**
 * 健康档案数据访问对象
 */
@Dao
interface HealthProfileDao {
    @Query("SELECT * FROM health_profiles ORDER BY updatedAt DESC")
    fun getAllProfiles(): Flow<List<HealthProfile>>
    
    @Query("SELECT * FROM health_profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: Long): HealthProfile?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: HealthProfile): Long
    
    @Update
    suspend fun updateProfile(profile: HealthProfile)
    
    @Delete
    suspend fun deleteProfile(profile: HealthProfile)
}
