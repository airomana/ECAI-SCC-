package com.eldercare.ai.ocr

import android.util.Log

/**
 * 菜名结构化提取器
 * 从OCR识别文本中提取可能的菜名
 */
object DishNameExtractor {
    
    private const val TAG = "DishNameExtractor"
    
    /**
     * 从OCR文本中提取菜名候选
     */
    fun extractDishCandidates(text: String): List<DishCandidate> {
        if (text.isBlank()) {
            Log.d(TAG, "输入文本为空")
            return emptyList()
        }
        
        Log.d(TAG, "开始提取菜名，原始文本：$text")
        
        val rawLines = text
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        Log.d(TAG, "分割后行数：${rawLines.size}")
        
        val candidates = mutableListOf<DishCandidate>()
        
        rawLines.forEachIndexed { index, line ->
            Log.d(TAG, "处理第${index}行：$line")
            val candidate = processLine(line, index)
            if (candidate != null) {
                Log.d(TAG, "提取到菜名：${candidate.name}")
                candidates.add(candidate)
            } else {
                Log.d(TAG, "该行未提取到菜名")
            }
        }
        
        // 去重并保持顺序
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<DishCandidate>()
        for (candidate in candidates) {
            val normalized = normalizeDishName(candidate.name)
            if (normalized !in seen && normalized.length >= 2) {
                seen.add(normalized)
                result.add(candidate.copy(name = normalized))
            }
        }
        
        Log.d(TAG, "最终提取到${result.size}个菜名：${result.map { it.name }}")
        
        return result.take(30) // 最多返回30个
    }
    
    private fun processLine(line: String, lineIndex: Int): DishCandidate? {
        // 移除价格、编号、单位等
        var cleaned = line
            // 移除末尾价格：¥12.5、12元、￥15、RMB 20等
            .replace(Regex("""[¥￥]\s*\d+(\.\d+)?\s*(元|RMB|CNY)?"""), "")
            // 移除独立的数字价格（如 "38" "28.00"）
            .replace(Regex("""\s+\d+(\.\d+)?\s*$"""), "")
            // 移除数量：2份、3个、1碗等
            .replace(Regex("""\s*\d+\s*(份|个|碗|盘|碟|斤|两|克|kg|g)\s*"""), "")
            // 移除编号：1.、2)、A.、①等
            .replace(Regex("""^[\d①②③④⑤⑥⑦⑧⑨⑩A-Za-z][\.、)）]\s*"""), "")
            // 移除常见后缀：推荐、热销、新品等
            .replace(Regex("""\s*(推荐|热销|新品|招牌|必点|限时|特价|例|大|中|小)\s*$"""), "")
            // 移除括号内容（如"（小份）"）
            .replace(Regex("""[（(][^）)]*[）)]"""), "")
            .trim()
        
        if (cleaned.isBlank()) return null
        
        // 如果清理后的文本太短，尝试使用原始行
        if (cleaned.length < 2 && line.length >= 2) {
            // 尝试提取中文字符
            val chineseOnly = line.replace(Regex("""[^\u4e00-\u9fff]"""), "")
            if (chineseOnly.length >= 2) {
                cleaned = chineseOnly
            }
        }
        
        // 基本过滤条件（更宽松）
        if (!isValidDishName(cleaned)) return null
        
        // 计算置信度
        val confidence = calculateConfidence(cleaned, line)
        
        return DishCandidate(
            name = cleaned,
            originalLine = line,
            lineIndex = lineIndex,
            confidence = confidence
        )
    }
    
    private fun isValidDishName(name: String): Boolean {
        // 长度检查：2-30个字符（进一步放宽限制）
        if (name.length !in 2..30) return false
        
        // 必须包含至少一个中文字符
        val chineseCount = name.count { it.code in 0x4e00..0x9fff }
        if (chineseCount < 1) return false
        
        // 排除纯数字
        if (Regex("""^[\d\s\.]+$""").matches(name)) return false
        
        // 只排除完全匹配的非菜名词汇（极简列表）
        val excludeExactPatterns = listOf(
            "菜单", "价格表", "目录", "总计", "合计", "小计"
        )
        if (excludeExactPatterns.any { name == it }) return false
        
        return true
    }
    
    private fun calculateConfidence(name: String, originalLine: String): Float {
        var confidence = 0.5f
        
        // 长度适中加分
        when (name.length) {
            in 2..6 -> confidence += 0.2f
            in 7..10 -> confidence += 0.15f
            else -> confidence += 0.1f
        }
        
        // 包含常见菜名关键词加分（扩展列表）
        val dishKeywords = listOf(
            // 食材
            "肉", "鱼", "鸡", "鸭", "虾", "蟹", "蛋", "牛", "羊", "猪", "排骨",
            "豆腐", "青菜", "白菜", "茄子", "土豆", "黄瓜", "番茄", "西红柿",
            // 烹饪方式
            "炒", "蒸", "煮", "炸", "烤", "炖", "烧", "煎", "焖", "红烧", "清蒸",
            "水煮", "干煸", "糖醋", "鱼香", "宫保", "麻婆",
            // 主食
            "饭", "面", "粥", "米", "饺", "包", "饼",
            // 汤
            "汤", "羹"
        )
        if (dishKeywords.any { name.contains(it) }) {
            confidence += 0.2f
        }
        
        // 如果原始行包含价格，很可能是菜名
        if (Regex("""[¥￥]\d+|\d+元""").containsMatchIn(originalLine)) {
            confidence += 0.15f
        }
        
        // 如果包含数字编号，可能是菜名
        if (Regex("""^\d+[\.、)]""").containsMatchIn(originalLine)) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0f, 1f)
    }
    
    private fun normalizeDishName(name: String): String {
        return name
            .replace(Regex("""\s+"""), "") // 移除所有空格
            .replace(Regex("""[（）()【】\[\]]"""), "") // 移除括号
            .trim()
    }
}

/**
 * 菜名候选
 */
data class DishCandidate(
    val name: String,
    val originalLine: String,
    val lineIndex: Int,
    val confidence: Float
)
