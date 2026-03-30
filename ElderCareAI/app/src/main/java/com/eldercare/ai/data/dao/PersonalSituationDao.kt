package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eldercare.ai.data.entity.PersonalSituationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalSituationDao {

    @Query("SELECT * FROM personal_situation WHERE id = 1 LIMIT 1")
    fun get(): Flow<PersonalSituationEntity?>

    @Query("SELECT * FROM personal_situation WHERE id = 1 LIMIT 1")
    suspend fun getOnce(): PersonalSituationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PersonalSituationEntity): Long

    @Update
    suspend fun update(entity: PersonalSituationEntity)
}
