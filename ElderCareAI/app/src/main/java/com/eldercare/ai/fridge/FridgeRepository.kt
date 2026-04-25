package com.eldercare.ai.fridge

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.eldercare.ai.data.dao.FridgeItemDao
import com.eldercare.ai.data.dao.FridgeScanDao
import com.eldercare.ai.data.dao.FridgeScanItemDao
import com.eldercare.ai.data.entity.FridgeItemEntity
import com.eldercare.ai.data.entity.FridgeScanEntity
import com.eldercare.ai.data.entity.FridgeScanItemEntity
import com.eldercare.ai.llm.LlmAuthException
import com.eldercare.ai.llm.LlmConfig
import com.eldercare.ai.llm.LlmRateLimitException
import com.eldercare.ai.utils.createFridgeScanPerformanceTracker
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
    private val rag = FridgeFoodRag(context)
    private val visionModelFast = "qwen-vl-plus"
    private val visionModelMax = "qwen-vl-max"
    
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
    suspend fun scanFridge(bitmap: Bitmap, highAccuracy: Boolean = false): ScanResult {
        val performanceTracker = createFridgeScanPerformanceTracker()
        try {
            val quality = foodDetector.assessImageQuality(bitmap)
            if (!quality.ok) {
                return ScanResult.Empty(quality.message ?: "看不清或没对准冰箱内部，建议补拍")
            }
            
            // 1. 检测食材
            val detection = detectFoodsWithModelLayering(bitmap, highAccuracy)
            val detectedFoods = detection.foods
            
            if (detectedFoods.isEmpty()) {
                // 优先使用 qualityHint（如离线提示），否则用通用提示
                val hint = detection.qualityHint ?: "看不清或没对准冰箱内部，建议补拍"
                return ScanResult.Empty(hint)
            }

            // 0. 清空旧数据（根据用户需求：每次拍照只显示本次结果）
            fridgeItemDao.deleteAll()
            
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
            
            performanceTracker.logTotal("拍冰箱成功总耗时")
            
            return ScanResult.Success(
                itemCount = newItems.size,
                items = newItems,
                unknownCount = detection.unknownCount,
                modelUsed = detection.modelUsed,
                wasUpgraded = detection.wasUpgraded
            )
        } catch (e: LlmAuthException) {
            performanceTracker.logTotal("拍冰箱(鉴权失败)总耗时")
            return ScanResult.Error("大模型API Key无效，请到设置里重新填写")
        } catch (e: LlmRateLimitException) {
            performanceTracker.logTotal("拍冰箱(限流失败)总耗时")
            return ScanResult.Error("大模型调用受限：额度不足或太频繁，请稍后再试")
        } catch (e: Exception) {
            performanceTracker.logTotal("拍冰箱(异常失败)总耗时")
            return ScanResult.Error("识别失败：${e.message}")
        }
    }

    private suspend fun detectFoodsWithModelLayering(bitmap: Bitmap, highAccuracy: Boolean): LayeredDetection {
        val llmAvailable = LlmConfig.isEnabled(context) && LlmConfig.isConfigured() && isNetworkAvailable()

        if (highAccuracy) {
            if (!llmAvailable) {
                // 离线时降级到端侧
                val local = foodDetector.detectFoods(bitmap, "local-yolo")
                val foods = rag.enrichFoods(local.foods)
                return LayeredDetection(foods = foods, unknownCount = foods.count { isUncertain(it) }, modelUsed = "local-yolo", wasUpgraded = false)
            }
            val max = foodDetector.detectFoods(bitmap, visionModelMax)
            val foods = rag.enrichFoods(max.foods)
            val unknownCount = foods.count { isUncertain(it) }
            return LayeredDetection(
                foods = foods,
                unknownCount = unknownCount,
                modelUsed = max.modelUsed,
                wasUpgraded = true
            )
        }

        val fast = foodDetector.detectFoods(bitmap, visionModelFast)
        val needsUpgrade = fast.foods.isEmpty() || shouldUpgrade(fast)
        if (!needsUpgrade) {
            val foods = rag.enrichFoods(fast.foods)
            val unknownCount = foods.count { isUncertain(it) }
            return LayeredDetection(
                foods = foods,
                unknownCount = unknownCount,
                modelUsed = fast.modelUsed,
                wasUpgraded = false,
                qualityHint = fast.qualityHint
            )
        }

        // 需要升级但离线时，直接用端侧结果（可能为空，带 qualityHint 提示）
        if (!llmAvailable) {
            val foods = rag.enrichFoods(fast.foods)
            return LayeredDetection(
                foods = foods,
                unknownCount = foods.count { isUncertain(it) },
                modelUsed = fast.modelUsed,
                wasUpgraded = false,
                qualityHint = fast.qualityHint ?: if (foods.isEmpty()) "当前离线，已使用端侧模型识别，请联网后重试" else null
            )
        }

        val max = foodDetector.detectFoods(bitmap, visionModelMax)
        val chosen = chooseBetter(fast, max)
        val foods = rag.enrichFoods(chosen.foods)
        val unknownCount = foods.count { isUncertain(it) }
        return LayeredDetection(
            foods = foods,
            unknownCount = unknownCount,
            modelUsed = chosen.modelUsed,
            wasUpgraded = true,
            qualityHint = chosen.qualityHint
        )
    }

    private fun isUncertain(food: DetectedFood): Boolean {
        val clarity = food.clarity?.trim().orEmpty()
        if (clarity == "看不清") return true
        if (food.confidence < 0.5f) return true
        val freshness = food.freshness?.trim().orEmpty()
        if (freshness == "未知") return true
        if (food.daysLeft == null) return true
        return false
    }

    private fun shouldUpgrade(result: FoodDetectionResult): Boolean {
        val total = result.foods.size
        if (total == 0) return true
        val ratio = result.unknownCount.toFloat() / total.toFloat()
        return ratio >= 0.4f || result.unknownCount >= 2
    }

    private fun chooseBetter(a: FoodDetectionResult, b: FoodDetectionResult): FoodDetectionResult {
        if (b.foods.isEmpty()) return a
        if (a.foods.isEmpty()) return b

        val aScore = scoreDetection(a)
        val bScore = scoreDetection(b)
        return if (bScore >= aScore) b else a
    }

    private fun scoreDetection(r: FoodDetectionResult): Int {
        val total = r.foods.size
        val known = total - r.unknownCount
        return known * 1000 + total
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
     * 检查网络是否可用
     */
    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
        val items: List<FridgeItemEntity>,
        val unknownCount: Int,
        val modelUsed: String,
        val wasUpgraded: Boolean
    ) : ScanResult()
    
    data class Empty(val message: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

private data class LayeredDetection(
    val foods: List<DetectedFood>,
    val unknownCount: Int,
    val modelUsed: String,
    val wasUpgraded: Boolean,
    val qualityHint: String? = null
)
