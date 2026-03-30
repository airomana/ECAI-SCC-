package com.eldercare.ai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eldercare.ai.data.entity.Dish
import kotlinx.coroutines.flow.Flow

/**
 * 菜品知识库 DAO：拍菜单时本地查询、预置数据
 */
@Dao
interface DishDao {

    @Query("SELECT * FROM dish ORDER BY name ASC")
    fun getAll(): Flow<List<Dish>>
    
    /** 获取所有菜品（同步版本，用于智能匹配） */
    @Query("SELECT * FROM dish ORDER BY name ASC")
    suspend fun getAllOnce(): List<Dish>

    /** 按菜名精确查询（用于 OCR 识别后的知识库匹配） */
    @Query("SELECT * FROM dish WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Dish?

    /** 按菜名模糊查询 */
    @Query("SELECT * FROM dish WHERE name LIKE '%' || :keyword || '%' LIMIT :limit")
    suspend fun searchByName(keyword: String, limit: Int = 20): List<Dish>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dishes: List<Dish>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dish: Dish): Long

    @Query("DELETE FROM dish")
    suspend fun deleteAll()
}
