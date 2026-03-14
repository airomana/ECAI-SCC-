package com.eldercare.ai.fridge

import android.content.Context
import android.graphics.Bitmap
import com.eldercare.ai.data.dao.FridgeItemDao
import com.eldercare.ai.data.dao.FridgeScanDao
import com.eldercare.ai.data.dao.FridgeScanItemDao
import com.eldercare.ai.data.entity.FridgeItemEntity
import com.eldercare.ai.data.entity.FridgeScanEntity
import com.eldercare.ai.data.entity.FridgeScanItemEntity
import com.eldercare.ai.llm.LlmAuthException
import com.eldercare.ai.llm.LlmConfig
import com.eldercare.ai.llm.LlmRateLimitException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 冰箱管理仓库
 * 负责食材识别、保质期计算和数据持久化
 */
class FridgeRepository(
    private val context: Context,
    private val fridgeItemDao: FridgeItemDao,
    private val fridgeScanDao: FridgeScanDao,
    private val fridgeScanItemDao: FridgeScanItemDao
) {
    
    private val foodDetector = FoodDetector(context)
    
    /**
     * 初始化
     */
    suspend fun initialize(): Boolean {
        return foodDetector.initialize()
    }
    
    /**
     * 扫描冰箱图片，识别食材并保存
     * 每次扫描前会清空旧数据，确保只显示本次识别结果
     */
    suspend fun scanFridge(bitmap: Bitmap): ScanResult {
        try {
            if (!LlmConfig.isEnabled(context)) {
                return ScanResult.Error("大模型未启用，请到设置里开启")
            }
            if (!LlmConfig.isConfigured()) {
                return ScanResult.Error("大模型未配置API Key，请到设置里填写")
            }

            // 0. 清空旧数据（根据用户需求：每次拍照只显示本次结果）
            fridgeItemDao.deleteAll()
            
            // 1. 检测食材
            val detectedFoods = foodDetector.detectFoods(bitmap)
            
            if (detectedFoods.isEmpty()) {
                return ScanResult.Empty("没有识别到食材，请重新拍摄")
            }
            
            // 2. 计算保质期并保存到数据库
            val currentTime = System.currentTimeMillis()
            val newItems = mutableListOf<FridgeItemEntity>()
            val historyItems = mutableListOf<FridgeScanItemEntity>()

            val scanId = fridgeScanDao.insert(
                FridgeScanEntity(
                    scannedAt = currentTime,
                    itemCount = detectedFoods.size
                )
            )
            
            for (food in detectedFoods) {
                val expiryTime = buildExpiryTime(food, currentTime)
                
                val item = FridgeItemEntity(
                    name = food.name,
                    category = food.category,
                    addedAt = currentTime,
                    expiryAt = expiryTime
                )
                
                fridgeItemDao.insert(item)
                newItems.add(item)

                historyItems.add(
                    FridgeScanItemEntity(
                        scanId = scanId,
                        name = food.name,
                        category = food.category,
                        addedAt = currentTime,
                        expiryAt = expiryTime
                    )
                )
            }

            fridgeScanItemDao.insertAll(historyItems)
            
            return ScanResult.Success(
                itemCount = newItems.size,
                items = newItems
            )
        } catch (e: LlmAuthException) {
            return ScanResult.Error("大模型API Key无效，请到设置里重新填写")
        } catch (e: LlmRateLimitException) {
            return ScanResult.Error("大模型调用受限：额度不足或太频繁，请稍后再试")
        } catch (e: Exception) {
            return ScanResult.Error("识别失败：${e.message}")
        }
    }

    private fun buildExpiryTime(food: DetectedFood, nowEpochMs: Long): Long {
        val freshness = food.freshness?.trim().orEmpty()
        val advice = food.advice?.trim().orEmpty()

        val spoiled = freshness.contains("疑似变质") ||
            freshness.contains("腐烂") ||
            freshness.contains("烂") ||
            advice.contains("别吃") ||
            advice.contains("扔") ||
            advice.contains("丢") ||
            advice.contains("坏了") ||
            advice.contains("腐烂") ||
            advice.contains("烂") ||
            advice.contains("发臭") ||
            advice.contains("长毛") ||
            advice.contains("霉") ||
            advice.contains("发霉") ||
            advice.contains("变质")
        if (spoiled) return nowEpochMs - 1L

        val daysLeft = food.daysLeft?.coerceIn(0, 365)
        if (daysLeft != null) {
            val cappedDays = when {
                freshness.contains("快坏") -> 0
                freshness.contains("一般") -> daysLeft.coerceAtMost(7)
                freshness.contains("新鲜") -> daysLeft
                else -> daysLeft
            }
            return endOfDayAfter(nowEpochMs, cappedDays)
        }

        return ShelfLifeCalculator.calculateExpiryTime(
            foodName = food.name,
            category = food.category,
            addedTime = nowEpochMs
        )
    }

    private fun endOfDayAfter(nowEpochMs: Long, daysAfter: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"), Locale.CHINA).apply {
            timeInMillis = nowEpochMs
            add(Calendar.DAY_OF_YEAR, daysAfter)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }
    
    /**
     * 获取所有食材（按过期时间排序）
     */
    fun getAllItems(): Flow<List<FridgeItemEntity>> {
        return fridgeItemDao.getAll()
    }
    
    /**
     * 获取即将过期的食材
     */
    fun getExpiringSoonItems(): Flow<List<FridgeItemEntity>> {
        return fridgeItemDao.getAll().map { items ->
            val currentTime = System.currentTimeMillis()
            items.filter { item ->
                val status = ShelfLifeCalculator.calculateFoodStatus(item.expiryAt, currentTime)
                status == FoodStatus.EXPIRING_SOON
            }
        }
    }
    
    /**
     * 获取已过期的食材
     */
    fun getExpiredItems(): Flow<List<FridgeItemEntity>> {
        return fridgeItemDao.getAll().map { items ->
            val currentTime = System.currentTimeMillis()
            items.filter { item ->
                val status = ShelfLifeCalculator.calculateFoodStatus(item.expiryAt, currentTime)
                status == FoodStatus.EXPIRED
            }
        }
    }
    
    /**
     * 删除食材
     */
    suspend fun deleteItem(itemId: Long) {
        fridgeItemDao.deleteById(itemId)
    }
    
    /**
     * 清空所有食材
     */
    suspend fun clearAll() {
        fridgeItemDao.deleteAll()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        foodDetector.release()
    }
}

/**
 * 扫描结果
 */
sealed class ScanResult {
    data class Success(
        val itemCount: Int,
        val items: List<FridgeItemEntity>
    ) : ScanResult()
    
    data class Empty(val message: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}
