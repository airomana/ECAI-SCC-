package com.eldercare.ai.fridge

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.util.Base64
import com.eldercare.ai.llm.LlmAuthException
import com.eldercare.ai.llm.LlmRateLimitException
import com.eldercare.ai.llm.LlmService
import java.io.ByteArrayOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 食材检测器
 * 使用通义千问（DashScope）视觉模型识别冰箱中的食材
 */
class FoodDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "FoodDetector"
    }
    
    private var isInitialized = false
    private val llmService = LlmService.getInstance(context)
    private val gson = Gson()
    
    /**
     * 初始化检测器
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext true
            isInitialized = true
            Log.d(TAG, "FoodDetector initialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FoodDetector", e)
            false
        }
    }
    
    /**
     * 检测图片中的食材
     */
    suspend fun detectFoods(bitmap: Bitmap, model: String): FoodDetectionResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Detector not initialized")
            return@withContext FoodDetectionResult(
                foods = emptyList(),
                unknownCount = 0,
                rawCount = 0,
                modelUsed = model
            )
        }
        
        try {
            val processed = preprocessBitmap(bitmap)

            // 将Bitmap转换为Base64
            val byteArrayOutputStream = ByteArrayOutputStream()
            processed.compress(Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            // 调用通义千问进行识别
            val jsonResult = llmService.analyzeImage(imageBase64, model)
            
            if (jsonResult.isNullOrBlank()) {
                Log.w(TAG, "LLM返回结果为空")
                return@withContext FoodDetectionResult(
                    foods = emptyList(),
                    unknownCount = 0,
                    rawCount = 0,
                    modelUsed = model
                )
            }
            
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val resultList: List<Map<String, Any?>> = try {
                gson.fromJson(jsonResult, type)
            } catch (e: Exception) {
                Log.w(TAG, "LLM返回JSON解析失败", e)
                emptyList()
            }

            val allowedFreshness = setOf("新鲜", "一般", "快坏", "疑似变质", "未知")

            val foods = ArrayList<DetectedFood>(resultList.size)
            var unknownCount = 0

            for (item in resultList) {
                val nameRaw = (item["name"] as? String)?.trim().orEmpty()
                if (nameRaw.isBlank()) continue

                val categoryRaw = (item["category"] as? String)?.trim().orEmpty()
                val category = normalizeCategory(categoryRaw)

                val freshnessRaw = (item["freshness"] as? String)?.trim()
                val freshness = if (freshnessRaw != null && allowedFreshness.contains(freshnessRaw)) freshnessRaw else "未知"

                val advice = (item["advice"] as? String)?.trim()?.takeUnless { it.isBlank() }

                val parsedDaysLeft = when (val raw = item["days_left"]) {
                    is Number -> raw.toInt()
                    is String -> raw.trim().toIntOrNull()
                    else -> null
                }
                val daysLeft = when (freshness) {
                    "疑似变质", "快坏" -> 0
                    else -> parsedDaysLeft
                }?.coerceIn(0, 365)

                val isUnknown = freshness == "未知" || daysLeft == null
                if (isUnknown) unknownCount++

                foods.add(
                    DetectedFood(
                        name = nameRaw,
                        category = category,
                        confidence = 1.0f,
                        boundingBox = BoundingBox(0, 0, 0, 0),
                        freshness = freshness,
                        daysLeft = daysLeft,
                        advice = advice
                    )
                )
            }

            return@withContext FoodDetectionResult(
                foods = foods,
                unknownCount = unknownCount,
                rawCount = resultList.size,
                modelUsed = model
            )
        } catch (e: LlmAuthException) {
            Log.e(TAG, e.message ?: "LLM认证失败", e)
            throw e
        } catch (e: LlmRateLimitException) {
            Log.e(TAG, e.message ?: "LLM请求受限", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect foods", e)
            FoodDetectionResult(
                foods = emptyList(),
                unknownCount = 0,
                rawCount = 0,
                modelUsed = model
            )
        }
    }

    private fun preprocessBitmap(original: Bitmap): Bitmap {
        val cropped = cropBorder(original, 0.90f)
        return resizeToMaxDimension(cropped, 1024)
    }

    private fun cropBorder(src: Bitmap, keepRatio: Float): Bitmap {
        val ratio = keepRatio.coerceIn(0.5f, 1.0f)
        if (ratio >= 1.0f) return src

        val w = src.width
        val h = src.height
        if (w <= 2 || h <= 2) return src

        val newW = max(1, (w * ratio).roundToInt())
        val newH = max(1, (h * ratio).roundToInt())
        val left = ((w - newW) / 2f).roundToInt().coerceIn(0, w - 1)
        val top = ((h - newH) / 2f).roundToInt().coerceIn(0, h - 1)
        val safeW = min(newW, w - left)
        val safeH = min(newH, h - top)
        if (safeW == w && safeH == h) return src
        return try {
            Bitmap.createBitmap(src, left, top, safeW, safeH)
        } catch (e: Exception) {
            src
        }
    }

    private fun resizeToMaxDimension(src: Bitmap, maxDim: Int): Bitmap {
        val maxD = maxDim.coerceAtLeast(256)
        val w = src.width
        val h = src.height
        val currentMax = max(w, h)
        if (currentMax <= maxD) return src

        val scale = maxD.toFloat() / currentMax.toFloat()
        val newW = max(1, (w * scale).roundToInt())
        val newH = max(1, (h * scale).roundToInt())
        return try {
            Bitmap.createScaledBitmap(src, newW, newH, true)
        } catch (e: Exception) {
            src
        }
    }

    private fun normalizeCategory(categoryRaw: String): String {
        val c = categoryRaw.trim()
        return when {
            c.isBlank() -> "其他"
            c.contains("蔬菜") -> "蔬菜"
            c.contains("水果") -> "水果"
            c.contains("蛋") || c.contains("奶") -> "蛋奶"
            c.contains("海鲜") || c.contains("水产") -> "海鲜"
            c.contains("肉") -> "肉类"
            c.contains("豆") -> "豆制品"
            c.contains("主食") || c.contains("米") || c.contains("面") -> "主食"
            else -> "其他"
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (isInitialized) {
            isInitialized = false
            Log.d(TAG, "FoodDetector released")
        }
    }
}

data class FoodDetectionResult(
    val foods: List<DetectedFood>,
    val unknownCount: Int,
    val rawCount: Int,
    val modelUsed: String
)

/**
 * 检测到的食材
 */
data class DetectedFood(
    val name: String,
    val category: String,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val freshness: String? = null,
    val daysLeft: Int? = null,
    val advice: String? = null
)

/**
 * 边界框
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
