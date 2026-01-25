package com.eldercare.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eldercare.ai.data.ElderCareDatabase
import com.eldercare.ai.data.dao.DiaryEntryDao
import com.eldercare.ai.data.dao.DishDao
import com.eldercare.ai.data.dao.FridgeItemDao
import com.eldercare.ai.data.dao.HealthProfileDao
import com.eldercare.ai.data.entity.DiaryEntryEntity
import com.eldercare.ai.data.entity.Dish
import com.eldercare.ai.data.entity.FridgeItemEntity
import com.eldercare.ai.data.entity.HealthProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 数据库仪器化测试：验证 CRUD、TypeConverters、各 DAO 是否正常工作。
 * 使用内存数据库，不依赖磁盘。
 *
 * 运行方式：连接真机或模拟器后执行
 *   ./gradlew connectedDebugAndroidTest
 * 或在 Android Studio 中右键此类 → Run 'ElderCareDatabaseTest'
 */
@RunWith(AndroidJUnit4::class)
class ElderCareDatabaseTest {

    private lateinit var db: ElderCareDatabase
    private lateinit var dishDao: DishDao
    private lateinit var healthProfileDao: HealthProfileDao
    private lateinit var diaryEntryDao: DiaryEntryDao
    private lateinit var fridgeItemDao: FridgeItemDao

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ElderCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dishDao = db.dishDao()
        healthProfileDao = db.healthProfileDao()
        diaryEntryDao = db.diaryEntryDao()
        fridgeItemDao = db.fridgeItemDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun dishDao_insertAndGetByName() = runBlocking {
        val dish = Dish(
            name = "测试菜",
            ingredients = listOf("成分A", "成分B"),
            cookingMethod = "炒",
            tags = listOf("低脂"),
            nutrients = "100kcal",
            suitableFor = listOf("健康人群"),
            notSuitableFor = emptyList(),
            plainDescription = "测试用大白话"
        )
        dishDao.insert(dish)
        val got = dishDao.getByName("测试菜")
        assertNotNull(got)
        assertEquals("测试菜", got?.name)
        assertEquals(2, got?.ingredients?.size)
        assertTrue(got!!.ingredients.contains("成分A"))
    }

    @Test
    fun dishDao_searchByName() = runBlocking {
        dishDao.insert(Dish(name = "红烧肉", ingredients = listOf("肉"), cookingMethod = "红烧", tags = listOf("高油"), nutrients = "", suitableFor = emptyList(), notSuitableFor = listOf("糖尿病"), plainDescription = "油大"))
        dishDao.insert(Dish(name = "清蒸肉", ingredients = listOf("肉"), cookingMethod = "清蒸", tags = emptyList(), nutrients = "", suitableFor = emptyList(), notSuitableFor = emptyList(), plainDescription = "清淡"))
        val list = dishDao.searchByName("肉", 10)
        assertTrue(list.size >= 2)
        assertTrue(list.any { it.name == "红烧肉" })
    }

    @Test
    fun healthProfileDao_insertAndGet_typeConverters() = runBlocking {
        val profile = HealthProfile(
            name = "张大爷",
            age = 70,
            diseases = listOf("高血压", "糖尿病"),
            allergies = listOf("海鲜")
        )
        healthProfileDao.insert(profile)
        val got = healthProfileDao.get().first()
        assertNotNull(got)
        assertEquals("张大爷", got?.name)
        assertEquals(70, got?.age)
        assertEquals(2, got?.diseases?.size)
        assertTrue(got!!.diseases.contains("高血压"))
        assertEquals(1, got.allergies.size)
        assertEquals("海鲜", got.allergies[0])
    }

    @Test
    fun healthProfileDao_update() = runBlocking {
        healthProfileDao.insert(HealthProfile(name = "李", age = 60, diseases = emptyList(), allergies = emptyList()))
        val p = healthProfileDao.getOnce()!!
        healthProfileDao.update(p.copy(name = "李奶奶", age = 65))
        val updated = healthProfileDao.getOnce()!!
        assertEquals("李奶奶", updated.name)
        assertEquals(65, updated.age)
    }

    @Test
    fun diaryEntryDao_insertAndGetAll() = runBlocking {
        val now = System.currentTimeMillis()
        diaryEntryDao.insert(DiaryEntryEntity(date = now, content = "今天吃了粥", emotion = "满意", aiResponse = "很好"))
        val list = diaryEntryDao.getAll().first()
        assertEquals(1, list.size)
        assertEquals("今天吃了粥", list[0].content)
        assertEquals("满意", list[0].emotion)
    }

    @Test
    fun fridgeItemDao_insertDeleteAndCount() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        fridgeItemDao.insert(FridgeItemEntity(name = "白菜", category = "蔬菜", addedAt = now, expiryAt = now + 3 * day))
        fridgeItemDao.insert(FridgeItemEntity(name = "萝卜", category = "蔬菜", addedAt = now, expiryAt = now + 5 * day))
        assertEquals(2, fridgeItemDao.getCount())
        val list = fridgeItemDao.getAll().first()
        assertEquals(2, list.size)
        val id = list[0].id
        fridgeItemDao.deleteById(id)
        assertEquals(1, fridgeItemDao.getCount())
    }
}
