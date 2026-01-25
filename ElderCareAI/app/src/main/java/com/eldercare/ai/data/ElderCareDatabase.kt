package com.eldercare.ai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eldercare.ai.data.dao.DishDao
import com.eldercare.ai.data.dao.HealthProfileDao
import com.eldercare.ai.data.dao.DiaryEntryDao
import com.eldercare.ai.data.dao.FridgeItemDao
import com.eldercare.ai.data.entity.Dish
import com.eldercare.ai.data.entity.FridgeItemEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.converters.Converters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ELDER_CARE_DB_NAME = "elder_care_db"
const val ELDER_CARE_DB_VERSION = 1

@Database(
    entities = [
        Dish::class,
        HealthProfile::class,
        DiaryEntryEntity::class,
        FridgeItemEntity::class
    ],
    version = ELDER_CARE_DB_VERSION,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ElderCareDatabase : RoomDatabase() {

    abstract fun dishDao(): DishDao
    abstract fun healthProfileDao(): HealthProfileDao
    abstract fun diaryEntryDao(): DiaryEntryDao
    abstract fun fridgeItemDao(): FridgeItemDao

    companion object {
        @Volatile
        private var INSTANCE: ElderCareDatabase? = null

        fun getDatabase(
            context: Context,
            scope: CoroutineScope
        ): ElderCareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ElderCareDatabase::class.java,
                    ELDER_CARE_DB_NAME
                )
                    .addCallback(ElderCareDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class ElderCareDatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { roomDb ->
                scope.launch(Dispatchers.IO) {
                    seedDishes(roomDb.dishDao())
                    seedFridgeItems(roomDb.fridgeItemDao())
                }
            }
        }

        private suspend fun seedDishes(dishDao: DishDao) {
            // 若已有数据则不再插入
            if (dishDao.getByName("红烧肉") != null) return

            dishDao.insertAll(
                listOf(
                    Dish(
                        name = "红烧肉",
                        ingredients = listOf("猪五花肉", "冰糖", "酱油", "料酒"),
                        cookingMethod = "红烧",
                        tags = listOf("高油", "高盐", "高热量"),
                        nutrients = "热量约500kcal/100g，脂肪较高",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("高血压", "高血脂", "减肥人群"),
                        plainDescription = "五花肉用糖和酱油烧的，很香但是油大"
                    ),
                    Dish(
                        name = "清蒸鱼",
                        ingredients = listOf("鲜鱼", "姜", "葱"),
                        cookingMethod = "清蒸",
                        tags = listOf("低脂", "高蛋白"),
                        nutrients = "热量约100kcal/100g，蛋白质丰富",
                        suitableFor = listOf("健康人群", "高血压", "糖尿病患者"),
                        notSuitableFor = listOf("海鲜过敏"),
                        plainDescription = "新鲜鱼用蒸的方式做的，很清淡"
                    ),
                    Dish(
                        name = "地三鲜",
                        ingredients = listOf("茄子", "土豆", "青椒"),
                        cookingMethod = "油炸",
                        tags = listOf("高油"),
                        nutrients = "油炸后吸油多，热量偏高",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("高血压", "减肥人群"),
                        plainDescription = "茄子土豆青椒一起炸的，油比较多"
                    ),
                    Dish(
                        name = "糖醋里脊",
                        ingredients = listOf("猪里脊", "白糖", "醋", "淀粉"),
                        cookingMethod = "油炸、糖醋",
                        tags = listOf("高糖", "高油"),
                        nutrients = "含糖约30g/份，GI值高",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("糖尿病", "减肥人群"),
                        plainDescription = "裹了很多糖和淀粉炸的，甜口，糖尿病患者要少吃"
                    ),
                    Dish(
                        name = "雪菜黄鱼",
                        ingredients = listOf("黄鱼", "雪里蕻"),
                        cookingMethod = "烧",
                        tags = listOf("高蛋白", "含盐"),
                        nutrients = "蛋白质丰富，雪菜较咸",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("高血压", "海鲜过敏"),
                        plainDescription = "黄鱼和雪菜一起烧的，咸鲜，血压高的要少碰"
                    ),
                    Dish(
                        name = "清炒时蔬",
                        ingredients = listOf("时令蔬菜", "蒜", "盐"),
                        cookingMethod = "清炒",
                        tags = listOf("低脂", "低盐"),
                        nutrients = "热量低，膳食纤维丰富",
                        suitableFor = listOf("健康人群", "高血压", "糖尿病", "减肥人群"),
                        notSuitableFor = emptyList(),
                        plainDescription = "青菜简单炒一下，清淡又健康"
                    )
                )
            )
        }

        private suspend fun seedFridgeItems(fridgeItemDao: FridgeItemDao) {
            if (fridgeItemDao.getCount() > 0) return
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            fridgeItemDao.insertAll(
                listOf(
                    FridgeItemEntity(name = "青菜", category = "蔬菜", addedAt = now - 4 * day, expiryAt = now - 1 * day),
                    FridgeItemEntity(name = "鸡蛋", category = "蛋奶", addedAt = now - 10 * day, expiryAt = now + 1 * day),
                    FridgeItemEntity(name = "牛奶", category = "蛋奶", addedAt = now - 1 * day, expiryAt = now + 5 * day),
                    FridgeItemEntity(name = "苹果", category = "蔬菜", addedAt = now - 2 * day, expiryAt = now + 3 * day)
                )
            )
        }
    }
}
