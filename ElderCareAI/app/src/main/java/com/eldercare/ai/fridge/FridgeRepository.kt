package com.eldercare.ai.fridge

import android.content.Context
import android.graphics.Bitmap
import com.eldercare.ai.data.dao.FridgeItemDao
import com.eldercare.ai.data.entity.FridgeItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 冰箱管理仓库
 * 负责食材识别、保质期计算和数据持久化
 */
class FridgeRepository(
    private val context: Context,
    private val fridgeItemDao: FridgeItemDao
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
            
            for (food in detectedFoods) {
                val expiryTime = ShelfLifeCalculator.calculateExpiryTime(
                    foodName = food.name,
                    category = food.category,
                    addedTime = currentTime
                )
                
                val item = FridgeItemEntity(
                    name = food.name,
                    category = food.category,
                    addedAt = currentTime,
                    expiryAt = expiryTime
                )
                
                fridgeItemDao.insert(item)
                newItems.add(item)
            }
            
            return ScanResult.Success(
                itemCount = newItems.size,
                items = newItems
            )
        } catch (e: Exception) {
            return ScanResult.Error("识别失败：${e.message}")
        }
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
