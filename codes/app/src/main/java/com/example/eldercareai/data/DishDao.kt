package com.example.eldercareai.data

import androidx.room.*
import com.example.eldercareai.model.Dish
import kotlinx.coroutines.flow.Flow

/**
 * 菜品数据访问对象
 */
@Dao
interface DishDao {
    @Query("SELECT * FROM dishes ORDER BY createdAt DESC")
    fun getAllDishes(): Flow<List<Dish>>
    
    @Query("SELECT * FROM dishes WHERE id = :dishId")
    suspend fun getDishById(dishId: Long): Dish?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDish(dish: Dish): Long
    
    @Update
    suspend fun updateDish(dish: Dish)
    
    @Delete
    suspend fun deleteDish(dish: Dish)
    
    @Query("DELETE FROM dishes")
    suspend fun deleteAllDishes()
}
