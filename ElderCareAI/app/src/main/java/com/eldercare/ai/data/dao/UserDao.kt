package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eldercare.ai.data.entity.User
import kotlinx.coroutines.flow.Flow

/**
 * 用户 DAO
 */
@Dao
interface UserDao {
    
    @Query("SELECT * FROM user WHERE phone = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): User?
    
    @Query("SELECT * FROM user WHERE phone = :phone LIMIT 1")
    fun getByPhoneFlow(phone: String): Flow<User?>
    
    @Query("SELECT * FROM user WHERE id = :userId LIMIT 1")
    suspend fun getById(userId: Long): User?
    
    @Query("SELECT * FROM user WHERE id = :userId LIMIT 1")
    fun getByIdFlow(userId: Long): Flow<User?>
    
    @Query("SELECT * FROM user WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getByInviteCode(inviteCode: String): User?
    
    @Query("SELECT * FROM user WHERE familyId = :familyId")
    suspend fun getByFamilyId(familyId: String): List<User>
    
    @Query("SELECT * FROM user WHERE familyId = :familyId AND role = :role")
    suspend fun getByFamilyIdAndRole(familyId: String, role: String): List<User>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long
    
    @Update
    suspend fun update(user: User)
    
    @Query("DELETE FROM user WHERE id = :userId")
    suspend fun delete(userId: Long)
    
    @Query("SELECT * FROM user LIMIT 1")
    suspend fun getCurrentUser(): User?
}
