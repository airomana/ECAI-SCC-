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
    suspend fun detectFoods(bitmap: Bitmap): List<DetectedFood> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Detector not initialized")
            return@withContext emptyList()
        }
        
        try {
            // 将Bitmap转换为Base64
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val imageBytes = byteArrayOutputStream.toByteArray()
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            // 调用通义千问进行识别
            val jsonResult = llmService.analyzeImage(imageBase64)
            
            if (jsonResult.isNullOrBlank()) {
                Log.w(TAG, "LLM返回结果为空")
                return@withContext emptyList()
            }
            
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val resultList: List<Map<String, Any?>> = gson.fromJson(jsonResult, type)
            
            resultList.map { 
                val name = (it["name"] as? String)?.trim().takeUnless { v -> v.isNullOrBlank() } ?: "未知食材"
                val category = (it["category"] as? String)?.trim().takeUnless { v -> v.isNullOrBlank() } ?: "其他"
                val freshness = (it["freshness"] as? String)?.trim()
                val advice = (it["advice"] as? String)?.trim()

                val daysLeft = when (val raw = it["days_left"]) {
                    is Number -> raw.toInt()
                    is String -> raw.toIntOrNull()
                    else -> null
                }

                DetectedFood(
                    name = name,
                    category = category,
                    confidence = 1.0f,
                    boundingBox = BoundingBox(0, 0, 0, 0),
                    freshness = freshness,
                    daysLeft = daysLeft,
                    advice = advice
                )
            }
        } catch (e: LlmAuthException) {
            Log.e(TAG, e.message ?: "LLM认证失败", e)
            throw e
        } catch (e: LlmRateLimitException) {
            Log.e(TAG, e.message ?: "LLM请求受限", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect foods", e)
            emptyList()
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
