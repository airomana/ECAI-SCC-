package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eldercare.ai.data.entity.FamilyLinkRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyLinkRequestDao {

    @Query("SELECT * FROM family_link_request WHERE parentUserId = :parentUserId AND status = :status ORDER BY createdAt DESC")
    fun getByParentAndStatus(parentUserId: Long, status: String): Flow<List<FamilyLinkRequestEntity>>

    @Query("SELECT * FROM family_link_request WHERE childUserId = :childUserId AND status = :status ORDER BY createdAt DESC")
    fun getByChildAndStatus(childUserId: Long, status: String): Flow<List<FamilyLinkRequestEntity>>

    @Query("SELECT * FROM family_link_request WHERE parentUserId = :parentUserId AND childUserId = :childUserId AND status = :status LIMIT 1")
    suspend fun getPending(parentUserId: Long, childUserId: Long, status: String = "pending"): FamilyLinkRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FamilyLinkRequestEntity): Long

    @Update
    suspend fun update(entity: FamilyLinkRequestEntity)
}
