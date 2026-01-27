package com.eldercare.ai.ocr

import com.eldercare.ai.data.entity.Dish

/**
 * 菜品知识库匹配器
 * 用于将OCR识别的菜名与知识库中的菜品进行智能匹配
 */
object DishKnowledgeMatcher {
    
    /**
     * 智能匹配菜品（支持模糊匹配和同义词）
     */
    fun matchDish(
        candidateName: String,
        allDishes: List<Dish>
    ): Dish? {
        if (allDishes.isEmpty()) return null
        
        val normalized = normalizeDishName(candidateName)
        
        // 1. 精确匹配
        allDishes.firstOrNull { normalizeDishName(it.name) == normalized }?.let {
            return it
        }
        
        // 2. 包含匹配（候选名包含在知识库菜名中，或反之）
        allDishes.firstOrNull { dish ->
            val dishNormalized = normalizeDishName(dish.name)
            normalized.contains(dishNormalized) || dishNormalized.contains(normalized)
        }?.let {
            return it
        }
        
        // 3. 相似度匹配（简单版：字符重叠度）
        val bestMatch = allDishes.maxByOrNull { dish ->
            calculateSimilarity(normalized, normalizeDishName(dish.name))
        }
        
        // 相似度阈值：至少50%匹配
        if (bestMatch != null && calculateSimilarity(normalized, normalizeDishName(bestMatch.name)) > 0.5f) {
            return bestMatch
        }
        
        return null
    }
    
    /**
     * 规范化菜名（用于匹配）
     */
    private fun normalizeDishName(name: String): String {
        return name
            .lowercase()
            .replace(Regex("""\s+"""), "") // 移除所有空格
            .replace(Regex("""[（）()【】\[\]""]"""), "") // 移除括号
            .replace(Regex("""[，,。.、]"""), "") // 移除标点
            .trim()
    }
    
    /**
     * 计算两个字符串的相似度（简单版Jaccard相似度）
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() || s2.isEmpty()) return 0f
        
        val set1 = s1.toSet()
        val set2 = s2.toSet()
        
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
    
    /**
     * 菜名同义词映射（常见变体）
     */
    private val dishSynonyms = mapOf(
        "红烧肉" to listOf("红烧猪肉", "红烧五花肉"),
        "糖醋里脊" to listOf("糖醋肉", "糖醋排骨"),
        "清蒸鱼" to listOf("蒸鱼", "白蒸鱼"),
        "地三鲜" to listOf("地三鲜", "东北地三鲜"),
        "清炒时蔬" to listOf("炒时蔬", "清炒蔬菜", "时蔬")
    )
    
    /**
     * 获取菜名的同义词列表
     */
    fun getSynonyms(dishName: String): List<String> {
        return dishSynonyms[dishName] ?: emptyList()
    }
}