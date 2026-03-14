package com.eldercare.ai.fridge

import android.content.Context
import android.util.Log
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.math.floor

/**
 * 保质期计算器
 * 根据食材类别和存储条件推算保质期
 */
object ShelfLifeCalculator {

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private const val MIN_SHELF_LIFE_DAYS = 1
    
    /**
     * 食材保质期规则（冷藏条件下，单位：天）
     */
    private val shelfLifeRules = mapOf(
        // 蔬菜类
        "青菜" to 3,
        "白菜" to 7,
        "卷心菜" to 7,
        "紫甘蓝" to 7,
        "西兰花" to 5,
        "花椰菜" to 5,
        "萝卜" to 14,
        "胡萝卜" to 14,
        "土豆" to 30,
        "西红柿" to 7,
        "黄瓜" to 5,
        "茄子" to 5,
        "青椒" to 7,
        "芹菜" to 7,
        "菠菜" to 3,
        "生菜" to 3,
        "油麦菜" to 3,
        "小白菜" to 3,
        "娃娃菜" to 5,
        "韭菜" to 3,
        "香菜" to 3,
        "葱" to 7,
        "姜" to 30,
        "蒜" to 30,
        "金针菇" to 3,
        "香菇" to 3,
        "蘑菇" to 3,
        
        // 水果类
        "苹果" to 14,
        "香蕉" to 5,
        "橙子" to 14,
        "柚子" to 21,
        "柠檬" to 21,
        "葡萄" to 7,
        "西瓜" to 3,
        "梨" to 14,
        "桃子" to 5,
        "草莓" to 3,
        "蓝莓" to 3,
        "樱桃" to 5,
        "猕猴桃" to 7,
        
        // 肉类
        "猪肉" to 3,
        "牛肉" to 3,
        "鸡肉" to 2,
        "鱼" to 2,
        "虾" to 2,
        "排骨" to 3,
        
        // 蛋奶类
        "鸡蛋" to 30,
        "牛奶" to 7,
        "奶" to 7,
        "酸奶" to 14,
        "奶酪" to 30,
        "黄油" to 30,
        
        // 豆制品
        "豆腐" to 3,
        "豆干" to 7,
        "豆浆" to 2,
        
        // 其他
        "米饭" to 2,
        "面条" to 2,
        "剩饭" to 1,
        "剩菜" to 1
    )
    
    /**
     * 类别默认保质期（天）
     */
    private val categoryDefaultShelfLife = mapOf(
        "蔬菜" to 5,
        "水果" to 7,
        "肉类" to 3,
        "海鲜" to 2,
        "蛋奶" to 14,
        "豆制品" to 5,
        "主食" to 2,
        "其他" to 3
    )

    private val foodNameAliases = mapOf(
        "番茄" to "西红柿",
        "圣女果" to "西红柿",
        "西蓝花" to "西兰花",
        "菜花" to "花椰菜",
        "包菜" to "卷心菜",
        "圆白菜" to "卷心菜",
        "甘蓝" to "卷心菜",
        "花菜" to "花椰菜",
        "西生菜" to "生菜",
        "莴苣" to "生菜",
        "大白菜" to "白菜",
        "白菜心" to "白菜"
    )

    private enum class FoodForm {
        WHOLE,
        CUT,
        OPENED,
        COOKED
    }

    private enum class FoodSubCategory {
        LEAFY_VEGETABLE,
        CRUCIFEROUS_VEGETABLE,
        ROOT_VEGETABLE,
        TUBER_VEGETABLE,
        FUNGUS_VEGETABLE,
        BERRY_FRUIT,
        CITRUS_FRUIT,
        TROPICAL_FRUIT,
        OTHER
    }

    private data class FoodContext(
        val normalizedName: String,
        val canonicalName: String,
        val normalizedCategory: String,
        val form: FoodForm,
        val subCategory: FoodSubCategory
    )

    private fun normalizeFoodName(foodName: String): String {
        val trimmed = foodName.trim()
        val noSpace = trimmed.replace(Regex("\\s+"), "")
        val noBracket = noSpace
            .replace(Regex("（.*?）"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
        return noBracket
            .replace("冷藏", "")
            .replace("保鲜", "")
            .replace("新鲜", "")
            .replace(Regex("[,，。.!！;；:：]"), "")
            .trim()
    }

    private fun normalizeCategory(category: String): String {
        val c = category.trim()
        return when {
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

    private fun inferFoodForm(originalName: String): FoodForm {
        val name = originalName
        val cookedKeywords = listOf("熟食", "剩菜", "剩饭", "凉拌", "拌", "炒", "煮", "炖", "卤", "烤", "炸", "蒸", "焖", "煎", "烩", "汤")
        if (cookedKeywords.any { name.contains(it) }) return FoodForm.COOKED

        val openedKeywords = listOf("开封", "拆封", "已开", "打开", "开口", "开了", "开盖")
        if (openedKeywords.any { name.contains(it) }) return FoodForm.OPENED

        val cutKeywords = listOf("切开", "切片", "切块", "切丝", "切段", "切好", "剁碎", "去皮", "削皮", "切")
        if (cutKeywords.any { name.contains(it) }) return FoodForm.CUT

        return FoodForm.WHOLE
    }

    private fun inferSubCategory(canonicalName: String, normalizedCategory: String): FoodSubCategory {
        if (normalizedCategory == "蔬菜") {
            val leafy = listOf("青菜", "菠菜", "生菜", "油麦菜", "小白菜", "娃娃菜", "韭菜", "香菜")
            if (leafy.any { canonicalName.contains(it) }) return FoodSubCategory.LEAFY_VEGETABLE

            val cruciferous = listOf("西兰花", "花椰菜", "卷心菜", "紫甘蓝", "白菜")
            if (cruciferous.any { canonicalName.contains(it) }) return FoodSubCategory.CRUCIFEROUS_VEGETABLE

            val roots = listOf("萝卜", "胡萝卜")
            if (roots.any { canonicalName.contains(it) }) return FoodSubCategory.ROOT_VEGETABLE

            val tubers = listOf("土豆", "山药", "红薯", "地瓜")
            if (tubers.any { canonicalName.contains(it) }) return FoodSubCategory.TUBER_VEGETABLE

            val fungus = listOf("金针菇", "香菇", "蘑菇", "平菇", "杏鲍菇", "菌")
            if (fungus.any { canonicalName.contains(it) }) return FoodSubCategory.FUNGUS_VEGETABLE
        }

        if (normalizedCategory == "水果") {
            val berries = listOf("草莓", "蓝莓", "树莓", "莓", "樱桃")
            if (berries.any { canonicalName.contains(it) }) return FoodSubCategory.BERRY_FRUIT

            val citrus = listOf("橙", "柚", "柠檬", "橘")
            if (citrus.any { canonicalName.contains(it) }) return FoodSubCategory.CITRUS_FRUIT

            val tropical = listOf("香蕉", "芒果", "菠萝", "凤梨", "猕猴桃")
            if (tropical.any { canonicalName.contains(it) }) return FoodSubCategory.TROPICAL_FRUIT
        }

        return FoodSubCategory.OTHER
    }

    private fun buildFoodContext(foodName: String, category: String): FoodContext {
        val normalizedName = normalizeFoodName(foodName)
        val normalizedCategory = normalizeCategory(category)
        val canonicalName = foodNameAliases[normalizedName] ?: normalizedName
        val form = inferFoodForm(foodName)
        val sub = inferSubCategory(canonicalName, normalizedCategory)
        return FoodContext(
            normalizedName = normalizedName,
            canonicalName = canonicalName,
            normalizedCategory = normalizedCategory,
            form = form,
            subCategory = sub
        )
    }
    
    /**
     * 计算过期时间
     * @param foodName 食材名称
     * @param category 食材类别
     * @param addedTime 放入时间（毫秒时间戳）
     * @return 过期时间（毫秒时间戳）
     */
    fun calculateExpiryTime(
        foodName: String,
        category: String,
        addedTime: Long
    ): Long {
        val shelfLifeDays = getShelfLifeDays(foodName, category)
        return addedTime + TimeUnit.DAYS.toMillis(shelfLifeDays.toLong())
    }
    
    /**
     * 获取保质期天数
     */
    fun getShelfLifeDays(foodName: String, category: String): Int {
        val ctx = buildFoodContext(foodName, category)

        val direct = shelfLifeRules[ctx.canonicalName]
        if (direct != null) {
            return applyFormModifier(direct, ctx)
        }

        val matchedKey = shelfLifeRules.keys
            .filter { key -> ctx.canonicalName.contains(key) || key.contains(ctx.canonicalName) }
            .maxByOrNull { it.length }
        if (matchedKey != null) {
            return applyFormModifier(shelfLifeRules.getValue(matchedKey), ctx)
        }

        val baseBySub = when (ctx.subCategory) {
            FoodSubCategory.LEAFY_VEGETABLE -> 3
            FoodSubCategory.CRUCIFEROUS_VEGETABLE -> 5
            FoodSubCategory.ROOT_VEGETABLE -> 14
            FoodSubCategory.TUBER_VEGETABLE -> 30
            FoodSubCategory.FUNGUS_VEGETABLE -> 3
            FoodSubCategory.BERRY_FRUIT -> 3
            FoodSubCategory.CITRUS_FRUIT -> 14
            FoodSubCategory.TROPICAL_FRUIT -> 5
            FoodSubCategory.OTHER -> null
        }
        if (baseBySub != null) return applyFormModifier(baseBySub, ctx)
        
        categoryDefaultShelfLife[ctx.normalizedCategory]?.let { return applyFormModifier(it, ctx) }
        
        return applyFormModifier(3, ctx)
    }

    private fun applyFormModifier(baseDays: Int, ctx: FoodContext): Int {
        val modified = when (ctx.form) {
            FoodForm.COOKED -> {
                when (ctx.normalizedCategory) {
                    "海鲜" -> 1.0
                    "肉类" -> 2.0
                    "主食" -> 2.0
                    else -> 2.0
                }
            }
            FoodForm.OPENED -> {
                when (ctx.normalizedCategory) {
                    "蛋奶" -> 3.0
                    else -> baseDays.toDouble() * 0.5
                }
            }
            FoodForm.CUT -> baseDays.toDouble() * 0.5
            FoodForm.WHOLE -> baseDays.toDouble()
        }

        val days = floor(modified).toInt().coerceAtLeast(MIN_SHELF_LIFE_DAYS)
        return days
    }
    
    /**
     * 计算食材状态
     */
    fun calculateFoodStatus(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): FoodStatus {
        val remainingMillis = expiryTime - currentTime
        return when {
            remainingMillis <= 0L -> FoodStatus.EXPIRED
            remainingMillis <= 2L * DAY_MILLIS -> FoodStatus.EXPIRING_SOON
            else -> FoodStatus.FRESH
        }
    }
    
    /**
     * 获取剩余天数
     */
    fun getRemainingDays(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): Long {
        val remainingMillis = expiryTime - currentTime
        if (remainingMillis <= 0L) return 0L
        return TimeUnit.MILLISECONDS.toDays(remainingMillis)
    }
    
    /**
     * 获取过期天数（已过期的情况）
     */
    fun getExpiredDays(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): Long {
        val expiredMillis = currentTime - expiryTime
        if (expiredMillis <= 0L) return 0L
        return TimeUnit.MILLISECONDS.toDays(expiredMillis)
    }
    
    /**
     * 生成状态描述文本
     */
    fun getStatusText(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): String {
        val status = calculateFoodStatus(expiryTime, currentTime)
        
        return when (status) {
            FoodStatus.EXPIRED -> {
                val expiredMillis = currentTime - expiryTime
                val days = getExpiredDays(expiryTime, currentTime)
                if (expiredMillis < DAY_MILLIS) "今天已经过期了" else "已经过期${days}天了"
            }
            FoodStatus.EXPIRING_SOON -> {
                val remainingMillis = expiryTime - currentTime
                val days = getRemainingDays(expiryTime, currentTime)
                if (remainingMillis < DAY_MILLIS) "不到1天就过期了" else "还有${days}天就过期了"
            }
            FoodStatus.FRESH -> {
                val remainingMillis = expiryTime - currentTime
                val days = getRemainingDays(expiryTime, currentTime)
                if (remainingMillis < DAY_MILLIS) "还能放不到1天" else "还能放${days}天"
            }
        }
    }
    
    /**
     * 生成建议文本
     */
    fun getAdviceText(foodName: String, category: String, expiryTime: Long, currentTime: Long = System.currentTimeMillis()): String {
        val status = calculateFoodStatus(expiryTime, currentTime)
        val ctx = buildFoodContext(foodName, category)
        val remainingMillis = expiryTime - currentTime
        val remainingDays = getRemainingDays(expiryTime, currentTime)
        val expiredDays = getExpiredDays(expiryTime, currentTime)
        val formTip = when (ctx.form) {
            FoodForm.COOKED -> "熟食"
            FoodForm.OPENED -> "已开封"
            FoodForm.CUT -> "已切开"
            FoodForm.WHOLE -> ""
        }
        
        return when (status) {
            FoodStatus.EXPIRED -> {
                val prefix = if (formTip.isNotEmpty()) "${formTip}，" else ""
                if (expiredDays <= 0L) "${prefix}已经过期了，别吃" else "${prefix}过期${expiredDays}天了，别吃"
            }
            FoodStatus.EXPIRING_SOON -> {
                val timeTip = if (remainingMillis < DAY_MILLIS) "不到1天就过期" else "还剩${remainingDays}天"
                val prefix = if (formTip.isNotEmpty()) "${formTip}，" else ""
                when (ctx.normalizedCategory) {
                    "肉类" -> "${prefix}${timeTip}，尽快煮熟吃，别反复解冻"
                    "海鲜" -> "${prefix}${timeTip}，尽快煮熟吃，尽量当天吃"
                    "蔬菜" -> {
                        when (ctx.subCategory) {
                            FoodSubCategory.LEAFY_VEGETABLE -> "${prefix}${timeTip}，叶菜先吃，尽快做熟"
                            FoodSubCategory.FUNGUS_VEGETABLE -> "${prefix}${timeTip}，菌菇尽快做熟，别久放"
                            else -> "${prefix}${timeTip}，尽快做熟"
                        }
                    }
                    "水果" -> {
                        when (ctx.subCategory) {
                            FoodSubCategory.BERRY_FRUIT -> "${prefix}${timeTip}，先吃莓类，别洗了久放"
                            else -> "${prefix}${timeTip}，先吃软的，切开别久放"
                        }
                    }
                    "蛋奶" -> "${prefix}${timeTip}，尽快吃/喝，开封后冷藏"
                    "豆制品" -> "${prefix}${timeTip}，尽快加热吃"
                    "主食" -> "${prefix}${timeTip}，尽快吃完，别反复加热"
                    else -> "${prefix}${timeTip}，尽快吃掉"
                }
            }
            FoodStatus.FRESH -> {
                val prefix = if (formTip.isNotEmpty()) "${formTip}，" else ""
                when (ctx.normalizedCategory) {
                    "肉类" -> "${prefix}分装冷藏或冷冻保存，吃前煮熟"
                    "海鲜" -> "${prefix}尽量当天吃，吃前煮熟"
                    "蔬菜" -> {
                        when (ctx.subCategory) {
                            FoodSubCategory.LEAFY_VEGETABLE -> "${prefix}洗净沥干密封冷藏，叶菜放前面先吃"
                            FoodSubCategory.FUNGUS_VEGETABLE -> "${prefix}密封冷藏，菌菇尽快做熟"
                            FoodSubCategory.ROOT_VEGETABLE, FoodSubCategory.TUBER_VEGETABLE -> "${prefix}干燥存放或冷藏，避免受潮发芽"
                            else -> "${prefix}洗净沥干密封冷藏，尽量尽快吃"
                        }
                    }
                    "水果" -> {
                        when (ctx.subCategory) {
                            FoodSubCategory.BERRY_FRUIT -> "${prefix}别洗先放，吃前再洗"
                            FoodSubCategory.CITRUS_FRUIT -> "${prefix}完整保存，表皮干燥更耐放"
                            else -> "${prefix}完整冷藏保存，切开后尽快吃"
                        }
                    }
                    "蛋奶" -> "${prefix}冷藏保存，开封后尽快吃/喝"
                    "豆制品" -> "${prefix}密封冷藏保存，尽量尽快吃"
                    "主食" -> "${prefix}密封冷藏保存，建议两天内吃完"
                    else -> "${prefix}密封冷藏保存，尽量尽快吃"
                }
            }
        }
    }
}

/**
 * 食材状态
 */
enum class FoodStatus {
    FRESH,          // 新鲜
    EXPIRING_SOON,  // 即将过期
    EXPIRED         // 已过期
}

class FridgeFoodRag(private val context: Context) {

    data class Rule(
        val ruleId: String,
        val foodNameStd: String,
        val aliases: String,
        val category: String,
        val keywords: String,
        val storage: String,
        val tempRangeC: String,
        val packState: String,
        val foodForm: String,
        val container: String,
        val processLevel: String,
        val notesApply: String,
        val shelfLifeMinDays: Int?,
        val shelfLifeMaxDays: Int?,
        val bucketCandidates: String,
        val defaultBucket: Int?,
        val expireWhen: String,
        val riskLevel: String,
        val spoilSigns: String,
        val mustDiscardIf: String,
        val adviceTemplate: String,
        val safeFallbackAdvice: String,
        val specialGroups: String,
        val version: String
    )

    private val rules: List<Rule> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadRulesFromAssets()
    }

    fun enrichFoods(
        foods: List<DetectedFood>,
        storage: String = "冷藏"
    ): List<DetectedFood> {
        if (foods.isEmpty()) return emptyList()
        return foods.map { enrichFood(it, storage) }
    }

    fun getAdviceText(
        foodName: String,
        category: String,
        expiryTime: Long,
        currentTime: Long = System.currentTimeMillis(),
        storage: String = "冷藏"
    ): String {
        val status = ShelfLifeCalculator.calculateFoodStatus(expiryTime, currentTime)
        val rule = findBestRule(
            foodName = foodName,
            category = category,
            storage = storage,
            packState = inferPackState(foodName),
            foodForm = inferFoodForm(foodName),
            processLevel = inferProcessLevel(foodName)
        )

        val ruleAdvice = when (status) {
            FoodStatus.EXPIRED -> {
                val core = rule?.mustDiscardIf?.takeUnless { it.isBlank() }
                if (core != null) "已经过期了，别吃。$core" else "已经过期了，别吃"
            }
            FoodStatus.EXPIRING_SOON -> {
                rule?.adviceTemplate?.takeUnless { it.isBlank() }
                    ?: rule?.safeFallbackAdvice?.takeUnless { it.isBlank() }
            }
            FoodStatus.FRESH -> {
                rule?.notesApply?.takeUnless { it.isBlank() }
                    ?: rule?.adviceTemplate?.takeUnless { it.isBlank() }
                    ?: rule?.safeFallbackAdvice?.takeUnless { it.isBlank() }
            }
        }

        return ruleAdvice ?: ShelfLifeCalculator.getAdviceText(foodName, category, expiryTime, currentTime)
    }

    private fun enrichFood(food: DetectedFood, storage: String): DetectedFood {
        val freshness = food.freshness?.trim().orEmpty()
        if (freshness == "疑似变质") {
            val advice = food.advice?.takeUnless { it.isBlank() } ?: "疑似变质，别吃，直接扔掉"
            return food.copy(daysLeft = 0, advice = advice)
        }
        if (freshness == "快坏") {
            val advice = food.advice?.takeUnless { it.isBlank() } ?: "看着快坏了，今天吃不完就别吃了"
            return food.copy(daysLeft = 0, advice = advice)
        }

        val confidence = food.confidence
        val clarity = food.clarity?.trim().orEmpty()
        val uncertain = clarity == "看不清" || confidence < 0.5f || freshness == "未知"

        val rule = findBestRule(
            foodName = food.name,
            category = food.category,
            storage = storage,
            packState = inferPackState(food.name),
            foodForm = inferFoodForm(food.name),
            processLevel = inferProcessLevel(food.name)
        )

        if (rule == null) {
            Log.d("FridgeFoodRag", "no_rule food=${food.name} cat=${food.category} storage=$storage clarity=$clarity conf=$confidence freshness=$freshness")
            if (uncertain) {
                return food.copy(daysLeft = null, advice = food.advice ?: "看不清或不确定，建议闻味道，不确定就别吃")
            }
            return food
        }

        if (uncertain) {
            val advice = rule.safeFallbackAdvice.takeUnless { it.isBlank() } ?: food.advice ?: "看不清或不确定，建议闻味道，不确定就别吃"
            Log.d("FridgeFoodRag", "rule_match_uncertain food=${food.name} rule=${rule.ruleId} storage=$storage pack=${inferPackState(food.name)} form=${inferFoodForm(food.name)} process=${inferProcessLevel(food.name)}")
            return food.copy(daysLeft = null, advice = advice)
        }

        val chosenDays = chooseDaysLeft(
            modelDaysLeft = food.daysLeft,
            rule = rule
        )

        val advice = rule.adviceTemplate.takeUnless { it.isBlank() }
            ?: rule.safeFallbackAdvice.takeUnless { it.isBlank() }
            ?: food.advice

        Log.d("FridgeFoodRag", "rule_match food=${food.name} rule=${rule.ruleId} chosenDays=$chosenDays storage=$storage pack=${inferPackState(food.name)} form=${inferFoodForm(food.name)} process=${inferProcessLevel(food.name)}")
        return food.copy(
            daysLeft = chosenDays,
            advice = advice
        )
    }

    private fun chooseDaysLeft(modelDaysLeft: Int?, rule: Rule): Int? {
        val candidates = parseBucketCandidates(rule.bucketCandidates)
        val defaultBucket = rule.defaultBucket?.let { bucketDownToCandidates(it, candidates) }
        val maxDays = rule.shelfLifeMaxDays?.let { bucketDownToCandidates(it, candidates) }

        val base = when {
            defaultBucket != null -> defaultBucket
            maxDays != null -> maxDays
            else -> null
        }

        val model = modelDaysLeft?.let { bucketDownToCandidates(it, candidates) }
        val merged = when {
            base == null -> model
            model == null -> base
            else -> minOf(base, model)
        }

        if (merged == null) return null
        if (maxDays != null) return minOf(merged, maxDays)
        return merged
    }

    private fun findBestRule(
        foodName: String,
        category: String,
        storage: String,
        packState: String,
        foodForm: String,
        processLevel: String
    ): Rule? {
        val nameKey = normalizeKey(foodName)
        val categoryKey = normalizeKey(category)
        val storageKey = normalizeKey(storage)
        val packKey = normalizeKey(packState)
        val formKey = normalizeKey(foodForm)
        val processKey = normalizeKey(processLevel)

        var best: Rule? = null
        var bestScore = Int.MIN_VALUE

        for (r in rules) {
            if (normalizeKey(r.storage) != storageKey) continue

            val score = scoreRule(
                r = r,
                nameKey = nameKey,
                categoryKey = categoryKey,
                packKey = packKey,
                formKey = formKey,
                processKey = processKey,
                originalName = foodName
            )

            if (score > bestScore) {
                bestScore = score
                best = r
            } else if (score == bestScore && best != null) {
                val bestDays = best.defaultBucket ?: best.shelfLifeMaxDays ?: Int.MAX_VALUE
                val currDays = r.defaultBucket ?: r.shelfLifeMaxDays ?: Int.MAX_VALUE
                if (currDays < bestDays) best = r
            }
        }

        return best?.takeIf { bestScore >= 600 }
    }

    private fun scoreRule(
        r: Rule,
        nameKey: String,
        categoryKey: String,
        packKey: String,
        formKey: String,
        processKey: String,
        originalName: String
    ): Int {
        var score = 0

        val stdKey = normalizeKey(r.foodNameStd)
        if (stdKey.isNotBlank() && stdKey == nameKey) score += 1200
        if (stdKey.isNotBlank() && (nameKey.contains(stdKey) || stdKey.contains(nameKey))) score += 700

        val aliasKeys = splitTokens(r.aliases)
        if (aliasKeys.any { it == nameKey }) score += 1100
        if (aliasKeys.any { it.isNotBlank() && nameKey.contains(it) }) score += 800

        val ruleCat = normalizeKey(r.category)
        if (ruleCat == categoryKey) score += 150
        if (ruleCat == normalizeKey("熟食") && (formKey == normalizeKey("熟") || processKey == normalizeKey("熟") || originalName.contains("剩菜") || originalName.contains("熟"))) score += 120

        val rulePack = normalizeKey(r.packState)
        if (rulePack.isNotBlank() && rulePack == packKey) score += 80
        if (rulePack.isNotBlank() && packKey.isBlank()) score += 20

        val ruleForm = normalizeKey(r.foodForm)
        if (ruleForm.isNotBlank() && ruleForm == formKey) score += 60
        if (ruleForm.isNotBlank() && formKey.isNotBlank() && ruleForm.contains(formKey)) score += 30

        val ruleProcess = normalizeKey(r.processLevel)
        if (ruleProcess.isNotBlank() && ruleProcess == processKey) score += 40

        val keywordTokens = splitTokens(r.keywords)
        for (k in keywordTokens) {
            if (k.isNotBlank() && originalName.contains(k)) score += 10
        }

        return score
    }

    private fun loadRulesFromAssets(): List<Rule> {
        val input = context.assets.open("fridge_food_data.csv")
        input.use { stream ->
            val bytes = stream.readBytes()
            val charset = chooseCharset(bytes)
            val text = bytes.toString(charset)
            val lines = text.split("\n")
            if (lines.isEmpty()) return emptyList()

            val header = splitCsvLine(lines.first().trimEnd('\r')).map { it.removePrefix("\uFEFF") }
            val index = header.withIndex().associate { it.value to it.index }

            val result = ArrayList<Rule>(lines.size)
            for (i in 1 until lines.size) {
                val rawLine = lines[i].trimEnd('\r')
                if (rawLine.isBlank()) continue
                val cols = splitCsvLine(rawLine)
                fun get(name: String): String {
                    val idx = index[name] ?: return ""
                    return cols.getOrNull(idx)?.trim().orEmpty()
                }

                val rule = Rule(
                    ruleId = get("rule_id"),
                    foodNameStd = get("food_name_std"),
                    aliases = get("aliases"),
                    category = get("category"),
                    keywords = get("keywords"),
                    storage = get("storage"),
                    tempRangeC = get("temp_range_c"),
                    packState = get("pack_state"),
                    foodForm = get("food_form"),
                    container = get("container"),
                    processLevel = get("process_level"),
                    notesApply = get("notes_apply"),
                    shelfLifeMinDays = get("shelf_life_min_days").toIntOrNull(),
                    shelfLifeMaxDays = get("shelf_life_max_days").toIntOrNull(),
                    bucketCandidates = get("bucket_candidates"),
                    defaultBucket = get("default_bucket").toIntOrNull(),
                    expireWhen = get("expire_when"),
                    riskLevel = get("risk_level"),
                    spoilSigns = get("spoil_signs"),
                    mustDiscardIf = get("must_discard_if"),
                    adviceTemplate = get("advice_template"),
                    safeFallbackAdvice = get("safe_fallback_advice"),
                    specialGroups = get("special_groups"),
                    version = get("version")
                )
                if (rule.ruleId.isNotBlank() && rule.foodNameStd.isNotBlank()) {
                    result.add(rule)
                }
            }
            Log.d("FridgeFoodRag", "loaded_rules count=${result.size} charset=${charset.name()} header=${header.size}")
            return result
        }
    }

    private fun chooseCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        val utf8Text = try {
            bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
        if (utf8Text.isNotBlank() && !utf8Text.contains('\uFFFD')) {
            return Charsets.UTF_8
        }

        return try {
            Charset.forName("GBK")
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i += 1
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                out.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(ch)
            }
            i += 1
        }
        out.add(sb.toString())
        return out
    }

    private fun splitTokens(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").map { normalizeKey(it) }.filter { it.isNotBlank() }.distinct()
    }

    private fun parseBucketCandidates(raw: String): IntArray {
        val parsed = raw.split("|").mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()
        if (parsed.isEmpty()) return intArrayOf(0, 1, 2, 3, 5, 7, 14)
        return parsed.toIntArray()
    }

    private fun bucketDownToCandidates(value: Int, candidates: IntArray): Int {
        val v = value.coerceIn(0, 365)
        var chosen = candidates.firstOrNull() ?: 0
        for (c in candidates) {
            if (c <= v) chosen = c else break
        }
        return chosen
    }

    private fun normalizeKey(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), "").lowercase()
    }

    private fun inferPackState(name: String): String {
        val openedKeywords = listOf("开封", "拆封", "已开", "打开", "开口", "开了", "开盖")
        if (openedKeywords.any { name.contains(it) }) return "开封"
        return "未开封"
    }

    private fun inferFoodForm(name: String): String {
        val cookedKeywords = listOf("熟食", "剩菜", "剩饭", "凉拌", "拌", "炒", "煮", "炖", "卤", "烤", "炸", "蒸", "焖", "煎", "烩", "汤")
        if (cookedKeywords.any { name.contains(it) }) return "熟"

        val cutKeywords = listOf("切开", "切片", "切块", "切丝", "切段", "切好", "剁碎", "去皮", "削皮", "切")
        if (cutKeywords.any { name.contains(it) }) return "切"

        val minceKeywords = listOf("绞肉", "肉馅", "剁肉", "碎肉")
        if (minceKeywords.any { name.contains(it) }) return "绞肉"

        return "整"
    }

    private fun inferProcessLevel(name: String): String {
        val cookedKeywords = listOf("熟食", "剩菜", "剩饭", "凉拌", "拌", "炒", "煮", "炖", "卤", "烤", "炸", "蒸", "焖", "煎", "烩", "汤")
        if (cookedKeywords.any { name.contains(it) }) return "熟"
        return "生"
    }
}
