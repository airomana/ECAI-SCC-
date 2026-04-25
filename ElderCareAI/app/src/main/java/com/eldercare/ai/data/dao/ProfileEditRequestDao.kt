package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eldercare.ai.data.entity.ProfileEditRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileEditRequestDao {

    @Query("SELECT * FROM profile_edit_request WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<ProfileEditRequestEntity>>

    @Query("SELECT * FROM profile_edit_request WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatusOnce(status: String): List<ProfileEditRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileEditRequestEntity): Long

    @Update
    suspend fun update(entity: ProfileEditRequestEntity)
}
