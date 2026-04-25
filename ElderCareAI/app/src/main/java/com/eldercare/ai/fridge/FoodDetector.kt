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

import com.eldercare.ai.yolo.YoloDetector
import java.io.File
import java.io.FileOutputStream

/**
 * 食材检测器
 * 支持端侧 (YOLO/NCNN) 与云端 (通义千问) 协同识别
 */
class FoodDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "FoodDetector"
    }
    
    private var isInitialized = false
    private val llmService = LlmService.getInstance(context)
    private val localDetector = YoloDetector()
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

            // 准备本地模型文件
            val modelDir = prepareModelFiles()
            if (modelDir != null) {
                val localInit = localDetector.nativeInit(modelDir)
                Log.d(TAG, "Local YoloDetector init: $localInit")
            }

            isInitialized = true
            Log.d(TAG, "FoodDetector initialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FoodDetector", e)
            false
        }
    }

    /**
     * 将 assets 中的模型文件复制到私有目录，供原生代码读取
     */
    private fun prepareModelFiles(): String? {
        try {
            val dir = File(context.filesDir, "fridge_models")
            if (!dir.exists()) dir.mkdirs()

            val modelFiles = arrayOf(
                "category.bin",
                "category.bin.param",
                "freshness_fruit_and_vegetables.bin",
                "freshness_fruit_and_vegetables.param"
            )

            for (fileName in modelFiles) {
                val outFile = File(dir, fileName)
                // 如果文件不存在或需要更新，则复制
                if (!outFile.exists()) {
                    context.assets.open("fridge/$fileName").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            return dir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare model files", e)
            return null
        }
    }

    /**
     * 检测图片中的食材（两步 RAG 增强）
     * 第一步：LLM 识别食材名称和初步新鲜度
     * 第二步：对不确定食材查 RAG 规则库，带规则重新调用 LLM 判断
     */
    suspend fun detectFoods(bitmap: Bitmap, model: String): FoodDetectionResult = withContext(Dispatchers.IO) {
        if (!isInitialized) initialize()

        try {
            val quality = assessImageQuality(bitmap)
            if (!quality.ok) {
                return@withContext FoodDetectionResult(
                    foods = emptyList(), unknownCount = 0, rawCount = 0,
                    modelUsed = model, qualityHint = quality.message
                )
            }

            val processed = preprocessBitmap(bitmap)
            val byteArrayOutputStream = ByteArrayOutputStream()
            processed.compress(Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream)
            val imageBase64 = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)

            // 第一步：识别食材名称和初步新鲜度
            val jsonResult = llmService.analyzeImage(imageBase64, model)
            if (jsonResult.isNullOrBlank()) {
                return@withContext FoodDetectionResult(
                    foods = emptyList(), unknownCount = 0, rawCount = 0,
                    modelUsed = model, qualityHint = null
                )
            }

            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val resultList: List<Map<String, Any?>> = try {
                gson.fromJson(jsonResult, type)
            } catch (e: Exception) {
                Log.w(TAG, "LLM返回JSON解析失败", e)
                emptyList()
            }

            val rawFoods = parseResultList(resultList)
            var foods = mergeFoods(rawFoods)

            // 第二步：对不确定食材查 RAG 规则，带规则重新判断
            val uncertainFoods = foods.filter { needsRagRefinement(it) }
            if (uncertainFoods.isNotEmpty()) {
                val rag = FridgeFoodRag(context)
                val foodsWithRules = uncertainFoods.mapNotNull { food ->
                    val rule = rag.findRuleSummary(food.name, food.category)
                    if (rule != null) Pair(food.name, rule) else null
                }
                if (foodsWithRules.isNotEmpty()) {
                    val refinedJson = llmService.analyzeImageWithRules(imageBase64, foodsWithRules, model)
                    if (!refinedJson.isNullOrBlank()) {
                        val refinedList: List<Map<String, Any?>> = try {
                            gson.fromJson(refinedJson, type)
                        } catch (e: Exception) { emptyList() }

                        // 用精化结果更新对应食材
                        val refinedMap = refinedList.associate { item ->
                            val name = (item["name"] as? String)?.trim().orEmpty()
                            name to item
                        }
                        foods = foods.map { food ->
                            val refined = refinedMap[food.name] ?: return@map food
                            val allowedFreshness = setOf("新鲜", "一般", "快坏", "疑似变质", "未知")
                            val freshnessRaw = (refined["freshness"] as? String)?.trim()
                            val freshness = if (freshnessRaw != null && allowedFreshness.contains(freshnessRaw)) freshnessRaw else food.freshness
                            val daysLeft = when (val raw = refined["days_left"]) {
                                is Number -> raw.toInt().coerceIn(0, 365)
                                is String -> raw.trim().toIntOrNull()?.coerceIn(0, 365)
                                else -> null
                            } ?: food.daysLeft
                            val spoilSigns = when (val raw = refined["spoil_signs_observed"]) {
                                is List<*> -> raw.mapNotNull { it as? String }.filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }
                                else -> food.spoilSignsObserved
                            }
                            food.copy(freshness = freshness, daysLeft = daysLeft, spoilSignsObserved = spoilSigns)
                        }
                        Log.d(TAG, "RAG refinement applied to ${foodsWithRules.size} foods")
                    }
                }
            }

            val unknownCount = foods.count { isUncertain(it) }
            return@withContext FoodDetectionResult(
                foods = foods, unknownCount = unknownCount,
                rawCount = resultList.size, modelUsed = model, qualityHint = null
            )
        } catch (e: LlmAuthException) { throw e }
        catch (e: LlmRateLimitException) { throw e }
        catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "网络不可用，降级到端侧模型", e)
            fallbackToLocal(bitmap, "当前离线，已使用端侧模型识别")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "网络IO异常，降级到端侧模型", e)
            fallbackToLocal(bitmap, "网络连接失败，已使用端侧模型识别")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect foods", e)
            fallbackToLocal(bitmap, null)
        }
    }

    /** 判断食材是否需要 RAG 精化：新鲜度未知、days_left 为空、或置信度低 */
    private fun needsRagRefinement(food: DetectedFood): Boolean {
        if (food.clarity == "看不清") return false  // 看不清的 RAG 也帮不上
        return food.freshness == "未知" || food.daysLeft == null || food.confidence < 0.6f
    }

    /** 解析 LLM 返回的 JSON 列表为 DetectedFood 列表 */
    private fun parseResultList(resultList: List<Map<String, Any?>>): List<DetectedFood> {
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
            val daysLeft = when (val raw = item["days_left"]) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull()
                else -> null
            }?.coerceIn(0, 365)
            val count = when (val raw = item["count"]) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull()
                else -> null
            }?.coerceIn(1, 99) ?: 1
            rawFoods.add(DetectedFood(
                name = nameRaw, category = category, confidence = confidence,
                boundingBox = BoundingBox(0, 0, 0, 0), freshness = freshness,
                daysLeft = daysLeft, advice = null, count = count,
                clarity = clarity, spoilSignsObserved = spoilSignsObserved
            ))
        }
        return rawFoods
    }

    /**
     * 解析端侧模型输出，合并 category + freshness 两个模型的结果
     *
     * category 模型输出：食材名（苹果、香蕉…）
     * freshness 模型输出：新鲜/腐烂前缀（新鲜苹果、腐烂苹果…）
     *
     * 策略：
     * 1. 先收集 category 结果作为基础食材列表
     * 2. 用 freshness 结果更新对应食材的新鲜度
     * 3. freshness 模型独有的结果（category 没检测到的）也加入
     */
    private fun parseLocalResults(
        results: Array<com.eldercare.ai.yolo.YoloDetector.DetectionResult>
    ): List<DetectedFood> {
        // 新鲜度前缀映射
        val freshnessMap = mapOf(
            "新鲜" to "新鲜",
            "腐烂" to "疑似变质"
        )
        // freshness 模型类别前缀
        val freshnessPrefixes = listOf("新鲜", "腐烂")

        // 分离两类结果
        val categoryResults = results.filter { r ->
            freshnessPrefixes.none { r.className.startsWith(it) }
        }
        val freshnessResults = results.filter { r ->
            freshnessPrefixes.any { r.className.startsWith(it) }
        }

        // 从 freshness 结果建立 食材名→新鲜度 映射（取置信度最高的）
        val freshnessLookup = mutableMapOf<String, Pair<String, Float>>()
        for (r in freshnessResults) {
            val prefix = freshnessPrefixes.firstOrNull { r.className.startsWith(it) } ?: continue
            val foodName = r.className.removePrefix(prefix)
            val freshness = freshnessMap[prefix] ?: "未知"
            val existing = freshnessLookup[foodName]
            if (existing == null || r.confidence > existing.second) {
                freshnessLookup[foodName] = Pair(freshness, r.confidence)
            }
        }

        val foods = mutableListOf<DetectedFood>()

        // 处理 category 结果
        for (r in categoryResults) {
            val freshness = freshnessLookup[r.className]?.first ?: "未知"
            foods.add(DetectedFood(
                name = r.className,
                category = normalizeCategory(r.className),
                confidence = r.confidence,
                boundingBox = BoundingBox(r.x, r.y, r.x + r.width, r.y + r.height),
                freshness = freshness,
                clarity = "清楚"
            ))
        }

        // freshness 模型检测到但 category 没有的食材（补充）
        for (r in freshnessResults) {
            val prefix = freshnessPrefixes.firstOrNull { r.className.startsWith(it) } ?: continue
            val foodName = r.className.removePrefix(prefix)
            if (foods.none { it.name == foodName }) {
                val freshness = freshnessMap[prefix] ?: "未知"
                foods.add(DetectedFood(
                    name = foodName,
                    category = normalizeCategory(foodName),
                    confidence = r.confidence,
                    boundingBox = BoundingBox(r.x, r.y, r.x + r.width, r.y + r.height),
                    freshness = freshness,
                    clarity = "清楚"
                ))
            }
        }

        Log.d(TAG, "parseLocalResults: category=${categoryResults.size} freshness=${freshnessResults.size} merged=${foods.size}")
        return foods
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
     * 降级到端侧模型
     */
    private fun fallbackToLocal(bitmap: Bitmap, hint: String?): FoodDetectionResult {
        // 端侧模型输入 640x640，传大图会导致内存暴涨甚至检测失败，先缩放
        val input = resizeToMaxDimension(bitmap, 1280)
        val localResults = localDetector.nativeDetectObjects(input)
        return if (localResults != null && localResults.isNotEmpty()) {
            Log.i(TAG, "Fallback: detected ${localResults.size} foods using local model")
            val foods = parseLocalResults(localResults)
            FoodDetectionResult(
                foods = foods,
                unknownCount = 0,
                rawCount = foods.size,
                modelUsed = "local-yolo",
                qualityHint = hint
            )
        } else {
            FoodDetectionResult(
                foods = emptyList(),
                unknownCount = 0,
                rawCount = 0,
                modelUsed = "local-yolo",
                qualityHint = hint ?: "识别失败，请调整角度重拍"
            )
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
