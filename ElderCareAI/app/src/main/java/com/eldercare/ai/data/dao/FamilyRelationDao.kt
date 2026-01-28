package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eldercare.ai.data.entity.FamilyRelation
import kotlinx.coroutines.flow.Flow

/**
 * 家庭关系 DAO
 */
@Dao
interface FamilyRelationDao {
    
    @Query("SELECT * FROM family_relation WHERE familyId = :familyId")
    suspend fun getByFamilyId(familyId: String): List<FamilyRelation>
    
    @Query("SELECT * FROM family_relation WHERE familyId = :familyId")
    fun getByFamilyIdFlow(familyId: String): Flow<List<FamilyRelation>>
    
    @Query("SELECT * FROM family_relation WHERE parentUserId = :userId OR childUserId = :userId")
    suspend fun getByUserId(userId: Long): List<FamilyRelation>
    
    @Query("SELECT * FROM family_relation WHERE parentUserId = :userId OR childUserId = :userId")
    fun getByUserIdFlow(userId: Long): Flow<List<FamilyRelation>>
    
    @Query("SELECT * FROM family_relation WHERE parentUserId = :parentId AND childUserId = :childId LIMIT 1")
    suspend fun getRelation(parentId: Long, childId: Long): FamilyRelation?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: FamilyRelation): Long
    
    @Query("DELETE FROM family_relation WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM family_relation WHERE familyId = :familyId")
    suspend fun deleteByFamilyId(familyId: String)
}
