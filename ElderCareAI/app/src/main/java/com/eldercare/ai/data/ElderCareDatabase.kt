package com.eldercare.ai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eldercare.ai.data.dao.DishDao
import com.eldercare.ai.data.dao.HealthProfileDao
import com.eldercare.ai.data.dao.DiaryEntryDao
import com.eldercare.ai.data.dao.FridgeItemDao
import com.eldercare.ai.data.dao.FridgeScanDao
import com.eldercare.ai.data.dao.FridgeScanItemDao
import com.eldercare.ai.data.dao.UserDao
import com.eldercare.ai.data.dao.FamilyRelationDao
import com.eldercare.ai.data.dao.PersonalSituationDao
import com.eldercare.ai.data.dao.EmergencyContactDao
import com.eldercare.ai.data.dao.ProfileEditRequestDao
import com.eldercare.ai.data.entity.Dish
import com.eldercare.ai.data.entity.FridgeItemEntity
import com.eldercare.ai.data.entity.FridgeScanEntity
import com.eldercare.ai.data.entity.FridgeScanItemEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.entity.User
import com.eldercare.ai.data.entity.FamilyRelation
import com.eldercare.ai.data.entity.PersonalSituationEntity
import com.eldercare.ai.data.entity.EmergencyContactEntity
import com.eldercare.ai.data.entity.ProfileEditRequestEntity
import com.eldercare.ai.data.converters.Converters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ELDER_CARE_DB_NAME = "elder_care_db"
const val ELDER_CARE_DB_VERSION = 5

@Database(
    entities = [
        Dish::class,
        HealthProfile::class,
        DiaryEntryEntity::class,
        FridgeItemEntity::class,
        FridgeScanEntity::class,
        FridgeScanItemEntity::class,
        User::class,
        FamilyRelation::class,
        PersonalSituationEntity::class,
        EmergencyContactEntity::class,
        ProfileEditRequestEntity::class
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
    abstract fun fridgeScanDao(): FridgeScanDao
    abstract fun fridgeScanItemDao(): FridgeScanItemDao
    abstract fun userDao(): UserDao
    abstract fun familyRelationDao(): FamilyRelationDao
    abstract fun personalSituationDao(): PersonalSituationDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun profileEditRequestDao(): ProfileEditRequestDao

    companion object {
        @Volatile
        private var INSTANCE: ElderCareDatabase? = null

        /**
         * 数据库迁移：从版本1到版本2
         * 添加User和FamilyRelation表
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    android.util.Log.d("ElderCareDatabase", "Starting migration from version 1 to 2")
                    
                    // 创建User表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS user (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            phone TEXT NOT NULL,
                            role TEXT NOT NULL,
                            inviteCode TEXT,
                            familyId TEXT,
                            nickname TEXT,
                            createdAt INTEGER NOT NULL,
                            lastLoginAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_phone ON user(phone)")
                    
                    // 创建FamilyRelation表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS family_relation (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            familyId TEXT NOT NULL,
                            parentUserId INTEGER NOT NULL,
                            childUserId INTEGER NOT NULL,
                            linkedAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_family_relation_familyId ON family_relation(familyId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_family_relation_parentUserId ON family_relation(parentUserId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_family_relation_childUserId ON family_relation(childUserId)")
                    
                    android.util.Log.d("ElderCareDatabase", "Migration completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ElderCareDatabase", "Migration failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    android.util.Log.d("ElderCareDatabase", "Starting migration from version 2 to 3")

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS fridge_scan (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            scannedAt INTEGER NOT NULL,
                            itemCount INTEGER NOT NULL,
                            note TEXT NOT NULL DEFAULT ''
                        )
                        """.trimIndent()
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_fridge_scan_scannedAt ON fridge_scan(scannedAt)")

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS fridge_scan_item (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            scanId INTEGER NOT NULL,
                            name TEXT NOT NULL,
                            category TEXT NOT NULL,
                            addedAt INTEGER NOT NULL,
                            expiryAt INTEGER NOT NULL,
                            FOREIGN KEY(scanId) REFERENCES fridge_scan(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_fridge_scan_item_scanId ON fridge_scan_item(scanId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_fridge_scan_item_expiryAt ON fridge_scan_item(expiryAt)")

                    android.util.Log.d("ElderCareDatabase", "Migration completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ElderCareDatabase", "Migration failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    android.util.Log.d("ElderCareDatabase", "Starting migration from version 3 to 4")

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS fridge_scan (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            scannedAt INTEGER NOT NULL,
                            itemCount INTEGER NOT NULL,
                            note TEXT NOT NULL DEFAULT ''
                        )
                        """.trimIndent()
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_fridge_scan_scannedAt ON fridge_scan(scannedAt)")

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS fridge_scan_item (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            scanId INTEGER NOT NULL,
                            name TEXT NOT NULL,
                            category TEXT NOT NULL,
                            addedAt INTEGER NOT NULL,
                            expiryAt INTEGER NOT NULL,
                            FOREIGN KEY(scanId) REFERENCES fridge_scan(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_fridge_scan_item_scanId ON fridge_scan_item(scanId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_fridge_scan_item_expiryAt ON fridge_scan_item(expiryAt)")

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS fridge_item_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            category TEXT NOT NULL,
                            addedAt INTEGER NOT NULL,
                            expiryAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    database.execSQL(
                        """
                        INSERT INTO fridge_item_new (id, name, category, addedAt, expiryAt)
                        SELECT id, name, category, addedAt, expiryAt FROM fridge_item
                        """.trimIndent()
                    )
                    database.execSQL("DROP TABLE fridge_item")
                    database.execSQL("ALTER TABLE fridge_item_new RENAME TO fridge_item")

                    android.util.Log.d("ElderCareDatabase", "Migration completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ElderCareDatabase", "Migration failed", e)
                    throw e
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    android.util.Log.d("ElderCareDatabase", "Starting migration from version 4 to 5")

                    database.execSQL("ALTER TABLE health_profile ADD COLUMN sex TEXT NOT NULL DEFAULT ''")
                    database.execSQL("ALTER TABLE health_profile ADD COLUMN birthYear INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE health_profile ADD COLUMN dietRestrictions TEXT NOT NULL DEFAULT '[]'")

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS personal_situation (
                            id INTEGER PRIMARY KEY NOT NULL,
                            city TEXT NOT NULL DEFAULT '',
                            livingAlone INTEGER NOT NULL DEFAULT 0,
                            tastePreferences TEXT NOT NULL DEFAULT '[]',
                            chewLevel TEXT NOT NULL DEFAULT '',
                            preferSoftFood INTEGER NOT NULL DEFAULT 0,
                            symptoms TEXT NOT NULL DEFAULT '[]',
                            bloodPressureStatus TEXT NOT NULL DEFAULT '',
                            bloodSugarStatus TEXT NOT NULL DEFAULT '',
                            shareHealth INTEGER NOT NULL DEFAULT 0,
                            shareDiet INTEGER NOT NULL DEFAULT 0,
                            shareContacts INTEGER NOT NULL DEFAULT 0,
                            updatedAt INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS emergency_contact (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL DEFAULT '',
                            phone TEXT NOT NULL DEFAULT '',
                            relation TEXT NOT NULL DEFAULT '',
                            isPrimary INTEGER NOT NULL DEFAULT 0,
                            updatedAt INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_emergency_contact_isPrimary ON emergency_contact(isPrimary)")

                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS profile_edit_request (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            status TEXT NOT NULL DEFAULT 'pending',
                            proposerUserId INTEGER NOT NULL DEFAULT 0,
                            proposerRole TEXT NOT NULL DEFAULT '',
                            payloadJson TEXT NOT NULL DEFAULT '',
                            createdAt INTEGER NOT NULL DEFAULT 0,
                            handledAt INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_profile_edit_request_status ON profile_edit_request(status)")

                    android.util.Log.d("ElderCareDatabase", "Migration completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("ElderCareDatabase", "Migration failed", e)
                    throw e
                }
            }
        }

        fun getDatabase(
            context: Context,
            scope: CoroutineScope
        ): ElderCareDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    android.util.Log.d("ElderCareDatabase", "Initializing database...")
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        ElderCareDatabase::class.java,
                        ELDER_CARE_DB_NAME
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(ElderCareDatabaseCallback(scope))
                    .fallbackToDestructiveMigrationOnDowngrade() // 降级时重建数据库
                    .allowMainThreadQueries() // 临时允许主线程查询，避免启动阻塞
                    .build()
                    INSTANCE = instance
                    android.util.Log.d("ElderCareDatabase", "Database initialized successfully")
                    instance
                } catch (e: Exception) {
                    android.util.Log.e("ElderCareDatabase", "Failed to initialize database", e)
                    throw e
                }
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
                    ),
                    // 添加更多常见菜品
                    Dish(
                        name = "宫保鸡丁",
                        ingredients = listOf("鸡胸肉", "花生", "干辣椒", "花椒"),
                        cookingMethod = "爆炒",
                        tags = listOf("高蛋白", "中油"),
                        nutrients = "蛋白质丰富，含花生",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("花生过敏", "痛风"),
                        plainDescription = "鸡肉和花生一起炒的，香辣下饭，但花生过敏的人不能吃"
                    ),
                    Dish(
                        name = "麻婆豆腐",
                        ingredients = listOf("豆腐", "肉末", "豆瓣酱", "花椒"),
                        cookingMethod = "烧",
                        tags = listOf("高蛋白", "含盐", "含辣"),
                        nutrients = "豆腐蛋白质丰富，但较咸较辣",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("高血压", "胃病"),
                        plainDescription = "豆腐和肉末一起烧的，又麻又辣，血压高的要少吃"
                    ),
                    Dish(
                        name = "白切鸡",
                        ingredients = listOf("整鸡", "姜", "葱"),
                        cookingMethod = "白煮",
                        tags = listOf("低脂", "高蛋白"),
                        nutrients = "热量适中，蛋白质丰富",
                        suitableFor = listOf("健康人群", "高血压", "糖尿病"),
                        notSuitableFor = emptyList(),
                        plainDescription = "整只鸡白水煮的，清淡健康，适合大部分人"
                    ),
                    Dish(
                        name = "鱼香肉丝",
                        ingredients = listOf("猪肉丝", "木耳", "胡萝卜", "豆瓣酱"),
                        cookingMethod = "炒",
                        tags = listOf("中油", "含盐"),
                        nutrients = "营养均衡，但油和盐较多",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("高血压"),
                        plainDescription = "肉丝和蔬菜一起炒的，味道不错，但有点咸"
                    ),
                    Dish(
                        name = "西红柿鸡蛋",
                        ingredients = listOf("鸡蛋", "西红柿", "糖", "盐"),
                        cookingMethod = "炒",
                        tags = listOf("高蛋白", "含糖"),
                        nutrients = "蛋白质和维生素丰富，但可能加糖",
                        suitableFor = listOf("健康人群", "高血压"),
                        notSuitableFor = listOf("糖尿病", "鸡蛋过敏"),
                        plainDescription = "鸡蛋和西红柿一起炒的，营养好，但糖尿病患者要注意糖分"
                    ),
                    Dish(
                        name = "水煮鱼",
                        ingredients = listOf("鱼片", "豆芽", "干辣椒", "花椒"),
                        cookingMethod = "水煮",
                        tags = listOf("高蛋白", "含辣", "中油"),
                        nutrients = "蛋白质丰富，但较辣较油",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("胃病", "海鲜过敏"),
                        plainDescription = "鱼片用水煮的，很辣，胃不好的人要少吃"
                    ),
                    Dish(
                        name = "蒸蛋",
                        ingredients = listOf("鸡蛋", "水", "盐"),
                        cookingMethod = "蒸",
                        tags = listOf("高蛋白", "低脂"),
                        nutrients = "蛋白质丰富，热量低",
                        suitableFor = listOf("健康人群", "高血压", "糖尿病", "减肥人群"),
                        notSuitableFor = listOf("鸡蛋过敏"),
                        plainDescription = "鸡蛋蒸的，很嫩很清淡，适合大部分人"
                    ),
                    Dish(
                        name = "回锅肉",
                        ingredients = listOf("五花肉", "青椒", "豆瓣酱"),
                        cookingMethod = "炒",
                        tags = listOf("高油", "高盐"),
                        nutrients = "热量高，脂肪多",
                        suitableFor = listOf("健康人群"),
                        notSuitableFor = listOf("高血压", "高血脂", "减肥人群"),
                        plainDescription = "五花肉先煮后炒，很香但油大，血压高的要少吃"
                    ),
                    Dish(
                        name = "凉拌黄瓜",
                        ingredients = listOf("黄瓜", "蒜", "醋", "盐"),
                        cookingMethod = "凉拌",
                        tags = listOf("低脂", "低热量"),
                        nutrients = "热量极低，水分多",
                        suitableFor = listOf("健康人群", "高血压", "糖尿病", "减肥人群"),
                        notSuitableFor = emptyList(),
                        plainDescription = "黄瓜凉拌的，清爽解腻，很适合夏天吃"
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
                    FridgeItemEntity(name = "苹果", category = "水果", addedAt = now - 2 * day, expiryAt = now + 3 * day)
                )
            )
        }
    }
}
