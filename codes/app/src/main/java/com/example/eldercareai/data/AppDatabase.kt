package com.example.eldercareai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.eldercareai.model.Dish
import com.example.eldercareai.model.HealthProfile

/**
 * 应用数据库
 * 用于本地存储菜品知识库和健康档案
 */
@Database(
    entities = [Dish::class, HealthProfile::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dishDao(): DishDao
    abstract fun healthProfileDao(): HealthProfileDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "elder_care_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
