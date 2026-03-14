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
import kotlin.math.sqrt

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

    data class ImageQualityResult(
        val ok: Boolean,
        val message: String?
    )
    
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
                modelUsed = model,
                qualityHint = null
            )
        }
        
        try {
            val quality = assessImageQuality(bitmap)
            if (!quality.ok) {
                return@withContext FoodDetectionResult(
                    foods = emptyList(),
                    unknownCount = 0,
                    rawCount = 0,
                    modelUsed = model,
                    qualityHint = quality.message
                )
            }

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
                    modelUsed = model,
                    qualityHint = null
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
            val allowedClarity = setOf("清楚", "一般", "看不清")

            val rawFoods = ArrayList<DetectedFood>(resultList.size)
            for (item in resultList) {
                val nameRaw = (item["name"] as? String)?.trim().orEmpty()
                if (nameRaw.isBlank()) continue

                val categoryRaw = (item["category"] as? String)?.trim().orEmpty()
                val category = normalizeCategory(categoryRaw)

                val clarityRaw = (item["clarity"] as? String)?.trim()
                val clarity = if (clarityRaw != null && allowedClarity.contains(clarityRaw)) clarityRaw else "一般"

                val confidence = when (val raw = item["confidence"]) {
                    is Number -> raw.toFloat()
                    is String -> raw.trim().toFloatOrNull()
                    else -> null
                }?.coerceIn(0f, 1f) ?: 0.5f

                val freshnessRaw = (item["freshness"] as? String)?.trim()
                val freshness = if (freshnessRaw != null && allowedFreshness.contains(freshnessRaw)) freshnessRaw else "未知"

                val spoilSignsObserved = when (val raw = item["spoil_signs_observed"]) {
                    is List<*> -> raw.mapNotNull { it as? String }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    is String -> raw.split("|", ",", "，").map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    else -> emptyList()
                }.takeIf { it.isNotEmpty() }

                val count = when (val raw = item["count"]) {
                    is Number -> raw.toInt()
                    is String -> raw.trim().toIntOrNull()
                    else -> null
                }?.coerceIn(1, 99) ?: 1

                rawFoods.add(
                    DetectedFood(
                        name = nameRaw,
                        category = category,
                        confidence = confidence,
                        boundingBox = BoundingBox(0, 0, 0, 0),
                        freshness = freshness,
                        daysLeft = null,
                        advice = null,
                        count = count,
                        clarity = clarity,
                        spoilSignsObserved = spoilSignsObserved
                    )
                )
            }

            val foods = mergeFoods(rawFoods)
            val unknownCount = foods.count { isUncertain(it) }

            return@withContext FoodDetectionResult(
                foods = foods,
                unknownCount = unknownCount,
                rawCount = resultList.size,
                modelUsed = model,
                qualityHint = null
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
                modelUsed = model,
                qualityHint = null
            )
        }
    }

    fun assessImageQuality(original: Bitmap): ImageQualityResult {
        val small = try {
            resizeToMaxDimension(original, 256)
        } catch (e: Exception) {
            original
        }

        val w = small.width
        val h = small.height
        if (w < 32 || h < 32) return ImageQualityResult(ok = false, message = "图片太小了，请重新拍一张")

        val pixels = IntArray(w * h)
        return try {
            small.getPixels(pixels, 0, w, 0, 0, w, h)

            var sum = 0.0
            var sumSq = 0.0
            var dark = 0
            var bright = 0
            var total = 0

            val step = 2
            var i = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    i = y * w + x
                    val c = pixels[i]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val l = 0.2126 * r + 0.7152 * g + 0.0722 * b
                    sum += l
                    sumSq += l * l
                    if (l < 20.0) dark++
                    if (l > 240.0) bright++
                    total++
                    x += step
                }
                y += step
            }

            val mean = sum / total.toDouble()
            val variance = (sumSq / total.toDouble()) - mean * mean
            val std = sqrt(max(0.0, variance))

            if (mean < 35.0 || dark.toDouble() / total.toDouble() > 0.75) {
                return ImageQualityResult(ok = false, message = "太暗了，请开灯或打开冰箱灯再拍")
            }
            if (mean > 225.0 || bright.toDouble() / total.toDouble() > 0.60) {
                return ImageQualityResult(ok = false, message = "太亮或反光强，请换角度避开反光再拍")
            }
            if (std < 18.0) {
                return ImageQualityResult(ok = false, message = "画面对比太低，看不清食材，请靠近一点并对准冰箱内部")
            }

            val lap = laplacianVariance(pixels, w, h)
            if (lap < 90.0) {
                return ImageQualityResult(ok = false, message = "有点糊，请拿稳手机，靠近一点再拍")
            }

            ImageQualityResult(ok = true, message = null)
        } catch (e: Exception) {
            ImageQualityResult(ok = true, message = null)
        }
    }

    private fun laplacianVariance(pixels: IntArray, w: Int, h: Int): Double {
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        val step = 2
        var y = 1
        while (y < h - 1) {
            var x = 1
            while (x < w - 1) {
                val c = grayAt(pixels, w, x, y)
                val l = grayAt(pixels, w, x - 1, y)
                val r = grayAt(pixels, w, x + 1, y)
                val u = grayAt(pixels, w, x, y - 1)
                val d = grayAt(pixels, w, x, y + 1)
                val v = (-4 * c + l + r + u + d).toDouble()
                sum += v
                sumSq += v * v
                count++
                x += step
            }
            y += step
        }
        if (count <= 0) return 0.0
        val mean = sum / count.toDouble()
        return (sumSq / count.toDouble()) - mean * mean
    }

    private fun grayAt(pixels: IntArray, w: Int, x: Int, y: Int): Int {
        val c = pixels[y * w + x]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return ((r * 3 + g * 6 + b) / 10)
    }

    private fun mergeFoods(list: List<DetectedFood>): List<DetectedFood> {
        if (list.isEmpty()) return emptyList()
        val map = LinkedHashMap<String, DetectedFood>(list.size)
        for (f in list) {
            val key = normalizeFoodKey(f.name)
            val existing = map[key]
            if (existing == null) {
                map[key] = f
                continue
            }

            val mergedCount = (existing.count + f.count).coerceIn(1, 99)
            val mergedConfidence = max(existing.confidence, f.confidence)
            val mergedClarity = worseClarity(existing.clarity, f.clarity)
            val mergedFreshness = worseFreshness(existing.freshness, f.freshness)
            val mergedDays = mergeDaysLeft(existing.daysLeft, f.daysLeft)
            val mergedCategory = if (existing.category != "其他") existing.category else f.category
            val mergedSpoilSignsObserved = mergeSigns(existing.spoilSignsObserved, f.spoilSignsObserved)

            map[key] = existing.copy(
                category = mergedCategory,
                confidence = mergedConfidence,
                freshness = mergedFreshness,
                daysLeft = mergedDays,
                count = mergedCount,
                clarity = mergedClarity,
                spoilSignsObserved = mergedSpoilSignsObserved
            )
        }
        return map.values.toList()
    }

    private fun normalizeFoodKey(name: String): String {
        return name.trim().replace(Regex("\\s+"), "").lowercase()
    }

    private fun worseClarity(a: String?, b: String?): String {
        val rank = mapOf("清楚" to 0, "一般" to 1, "看不清" to 2)
        val ra = rank[a] ?: 1
        val rb = rank[b] ?: 1
        return if (ra >= rb) a ?: "一般" else b ?: "一般"
    }

    private fun worseFreshness(a: String?, b: String?): String {
        val rank = mapOf("新鲜" to 0, "一般" to 1, "快坏" to 2, "未知" to 3, "疑似变质" to 4)
        val ra = rank[a] ?: 4
        val rb = rank[b] ?: 4
        return if (ra >= rb) a ?: "未知" else b ?: "未知"
    }

    private fun mergeDaysLeft(a: Int?, b: Int?): Int? {
        if (a == null) return b
        if (b == null) return a
        return min(a, b)
    }

    private fun isUncertain(food: DetectedFood): Boolean {
        val clarity = food.clarity?.trim().orEmpty()
        if (clarity == "看不清") return true
        if (food.confidence < 0.5f) return true
        val freshness = food.freshness?.trim().orEmpty()
        if (freshness == "未知") return true
        return false
    }

    private fun mergeSigns(a: List<String>?, b: List<String>?): List<String>? {
        if (a.isNullOrEmpty()) return b
        if (b.isNullOrEmpty()) return a
        return (a + b).map { it.trim() }.filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }
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
            c.contains("熟") -> "熟食"
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
    val modelUsed: String,
    val qualityHint: String? = null
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
    val advice: String? = null,
    val count: Int = 1,
    val clarity: String? = null,
    val spoilSignsObserved: List<String>? = null
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
