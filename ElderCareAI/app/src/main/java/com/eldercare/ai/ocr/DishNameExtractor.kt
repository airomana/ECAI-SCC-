package com.eldercare.ai.ocr

import android.util.Log

/**
 * 菜名结构化提取器 V2.0
 * 从OCR识别文本中提取可能的菜名
 * 更细化和完善的识别规则
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
        
        // 预处理：统一换行符
        val normalizedText = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        
        val rawLines = normalizedText
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        Log.d(TAG, "分割后行数：${rawLines.size}")
        
        val candidates = mutableListOf<DishCandidate>()
        
        rawLines.forEachIndexed { index, line ->
            Log.d(TAG, "处理第${index}行：$line")
            
            // 尝试多种提取策略
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
        
        // 按置信度排序
        val sortedResult = result.sortedByDescending { it.confidence }
        
        Log.d(TAG, "最终提取到${sortedResult.size}个菜名：${sortedResult.map { it.name }}")
        
        return sortedResult.take(30) // 最多返回30个
    }
    
    private fun processLine(line: String, lineIndex: Int): DishCandidate? {
        // ========== 第一步：移除价格信息 ==========
        var cleaned = removePriceInfo(line)
        
        // ========== 第二步：移除编号和序号 ==========
        cleaned = removeNumbering(cleaned)
        
        // ========== 第三步：移除单位和小份信息 ==========
        cleaned = removeUnits(cleaned)
        
        // ========== 第四步：移除常见后缀标签 ==========
        cleaned = removeSuffixTags(cleaned)
        
        // ========== 第五步：移除括号内容 ==========
        cleaned = removeBrackets(cleaned)
        
        // ========== 第六步：清理特殊字符 ==========
        cleaned = cleanSpecialChars(cleaned)
        
        if (cleaned.isBlank()) return null
        
        // ========== 第七步：如果清理后太短，尝试提取纯中文 ==========
        if (cleaned.length < 2 && line.length >= 2) {
            val chineseOnly = extractChineseOnly(line)
            if (chineseOnly.length >= 2) {
                cleaned = chineseOnly
            }
        }
        
        // ========== 第八步：验证是否为有效菜名 ==========
        if (!isValidDishName(cleaned)) return null
        
        // ========== 第九步：计算置信度 ==========
        val confidence = calculateConfidence(cleaned, line)
        
        return DishCandidate(
            name = cleaned,
            originalLine = line,
            lineIndex = lineIndex,
            confidence = confidence
        )
    }
    
    /**
     * 移除价格信息（更完善的规则）
     */
    private fun removePriceInfo(line: String): String {
        return line
            // 移除末尾价格：¥12.5、12元、￥15、RMB 20、$10等
            .replace(Regex("""[¥￥$]\s*\d+(\.\d+)?\s*(元|RMB|CNY|USD)?\s*$"""), "")
            // 移除独立的价格数字（如 "38" "28.00" "128"）
            .replace(Regex("""\s+\d{1,3}(\.\d{1,2})?\s*$"""), "")
            // 移除价格范围：12-15元、¥10~20
            .replace(Regex("""[¥￥$]?\s*\d+\s*[-~至]\s*\d+\s*(元|RMB)?"""), "")
            // 移除"起"字价格：¥10起
            .replace(Regex("""[¥￥$]\s*\d+\s*起"""), "")
            .trim()
    }
    
    /**
     * 移除编号和序号（更完善的规则）
     */
    private fun removeNumbering(line: String): String {
        return line
            // 移除数字编号：1.、2)、3、①、②等
            .replace(Regex("""^[\d①②③④⑤⑥⑦⑧⑨⑩⑴⑵⑶⑷⑸⑹⑺⑻⑼⑽]\s*[\.、)）．]\s*"""), "")
            // 移除字母编号：A.、B)、a.等
            .replace(Regex("""^[A-Za-z]\s*[\.、)）．]\s*"""), "")
            // 移除罗马数字编号：I.、II)、III.等
            .replace(Regex("""^[IVX]+[\.、)）．]\s*"""), "")
            // 移除"第X"：第1、第2等
            .replace(Regex("""^第[一二三四五六七八九十\d]+\s*"""), "")
            .trim()
    }
    
    /**
     * 移除单位信息（更完善的规则）
     */
    private fun removeUnits(line: String): String {
        return line
            // 移除数量单位：2份、3个、1碗、半斤等
            .replace(Regex("""\s*\d+(\.\d+)?\s*(份|个|碗|盘|碟|斤|两|克|kg|g|ml|升|L)\s*"""), "")
            // 移除规格：大份、中份、小份、例、位等
            .replace(Regex("""\s*(大|中|小|特大|超大)?\s*(份|例|位|人份)\s*$"""), "")
            // 移除重量：500g、1kg等
            .replace(Regex("""\s*\d+(\.\d+)?\s*(g|kg|克|千克|斤|两)\s*"""), "")
            .trim()
    }
    
    /**
     * 移除常见后缀标签（更完善的规则）
     */
    private fun removeSuffixTags(line: String): String {
        return line
            // 移除推荐标签：推荐、热销、新品、招牌、必点、限时、特价、优惠等
            .replace(Regex("""\s*(推荐|热销|新品|招牌|必点|限时|特价|优惠|促销|打折|热卖|爆款)\s*$"""), "")
            // 移除状态标签：售罄、缺货、暂停等
            .replace(Regex("""\s*(售罄|缺货|暂停|下架|已售完)\s*$"""), "")
            // 移除分类标签：主食、汤品、小食、饮品、甜品等
            .replace(Regex("""\s*(主食|汤品|小食|饮品|甜品|凉菜|热菜|素菜|荤菜)\s*$"""), "")
            .trim()
    }
    
    /**
     * 移除括号内容（更完善的规则）
     */
    private fun removeBrackets(line: String): String {
        return line
            // 移除各种括号：()、[]、{}、【】、（）等
            .replace(Regex("""[（(][^）)]*[）)]"""), "")  // 中文和英文圆括号
            .replace(Regex("""【[^】]*】"""), "")  // 中文方括号【】
            .replace(Regex("""\[[^\]]*\]"""), "")  // 英文方括号[]
            .replace(Regex("""[{][^}]*[}]"""), "")  // 花括号
            .replace(Regex("""[〈<][^〉>]*[〉>]"""), "")  // 尖括号
            .trim()
    }
    
    /**
     * 清理特殊字符
     */
    private fun cleanSpecialChars(line: String): String {
        return line
            // 移除多余空格
            .replace(Regex("""\s+"""), " ")
            // 移除特殊符号（保留中文、英文、数字、常见标点）
            .replace(Regex("""[^\u4e00-\u9fff\w\s，。、·]"""), "")
            .trim()
    }
    
    /**
     * 提取纯中文字符
     */
    private fun extractChineseOnly(line: String): String {
        return line
            .replace(Regex("""[^\u4e00-\u9fff]"""), "")
            .trim()
    }
    
    /**
     * 验证是否为有效菜名（更完善的规则）
     */
    private fun isValidDishName(name: String): Boolean {
        // 长度检查：2-30个字符
        if (name.length !in 2..30) return false
        
        // 必须包含至少一个中文字符
        val chineseCount = name.count { it.code in 0x4e00..0x9fff }
        if (chineseCount < 1) return false
        
        // 排除纯数字
        if (Regex("""^[\d\s\.]+$""").matches(name)) return false
        
        // 排除纯英文（除非是常见菜名如"Pizza"）
        val englishOnly = name.replace(Regex("""[^\w\s]"""), "").trim()
        if (englishOnly.length > 3 && chineseCount == 0) {
            // 允许一些常见的英文菜名
            val allowedEnglish = listOf("pizza", "salad", "soup", "cake", "coffee", "tea")
            if (!allowedEnglish.any { englishOnly.lowercase().contains(it) }) {
                return false
            }
        }
        
        // 排除明确的非菜名词汇
        val excludeExactPatterns = listOf(
            "菜单", "价格表", "目录", "总计", "合计", "小计", "总计",
            "主食类", "汤品类", "小食类", "饮品类", "甜品类",
            "特别推荐", "厨师推荐", "本店特色", "今日特价",
            "第", "页", "共", "元", "价格", "单价"
        )
        if (excludeExactPatterns.any { name == it || name.contains(it) }) return false
        
        // 排除分类标题（通常以"类"、"品"结尾且较短）
        if ((name.endsWith("类") || name.endsWith("品")) && name.length <= 4) {
            return false
        }
        
        // 排除纯标点符号
        if (Regex("""^[，。、·\s]+$""").matches(name)) return false
        
        return true
    }
    
    /**
     * 计算置信度（更完善的规则）
     */
    private fun calculateConfidence(name: String, originalLine: String): Float {
        var confidence = 0.5f
        
        // 长度适中加分
        when (name.length) {
            in 2..6 -> confidence += 0.25f  // 2-6字最佳
            in 7..10 -> confidence += 0.2f  // 7-10字良好
            in 11..15 -> confidence += 0.15f  // 11-15字可接受
            else -> confidence += 0.1f  // 其他
        }
        
        // 包含常见菜名关键词加分（扩展列表）
        val dishKeywords = listOf(
            // 食材（肉类）
            "肉", "鱼", "鸡", "鸭", "鹅", "虾", "蟹", "蛋", "牛", "羊", "猪", 
            "排骨", "里脊", "五花", "瘦肉", "肥肉", "内脏", "肝", "肾", "心",
            // 食材（蔬菜）
            "豆腐", "青菜", "白菜", "茄子", "土豆", "黄瓜", "番茄", "西红柿",
            "萝卜", "冬瓜", "南瓜", "丝瓜", "苦瓜", "豆角", "豆芽", "韭菜",
            "芹菜", "菠菜", "生菜", "包菜", "花菜", "西兰花",
            // 食材（其他）
            "蘑菇", "香菇", "木耳", "海带", "紫菜", "粉丝", "粉条",
            // 烹饪方式
            "炒", "蒸", "煮", "炸", "烤", "炖", "烧", "煎", "焖", "卤", "酱",
            "红烧", "清蒸", "水煮", "干煸", "糖醋", "鱼香", "宫保", "麻婆",
            "白切", "白灼", "凉拌", "爆炒", "干锅", "火锅",
            // 主食
            "饭", "面", "粥", "米", "饺", "包", "饼", "糕", "条",
            // 汤
            "汤", "羹", "煲",
            // 其他
            "丝", "片", "块", "丁", "条", "丸", "球"
        )
        
        var keywordMatchCount = 0
        dishKeywords.forEach { keyword ->
            if (name.contains(keyword)) {
                keywordMatchCount++
            }
        }
        
        // 关键词匹配越多，置信度越高
        when (keywordMatchCount) {
            0 -> confidence += 0f
            1 -> confidence += 0.15f
            2 -> confidence += 0.25f
            else -> confidence += 0.3f
        }
        
        // 如果原始行包含价格，很可能是菜名
        if (Regex("""[¥￥$]\d+|\d+元""").containsMatchIn(originalLine)) {
            confidence += 0.2f
        }
        
        // 如果包含数字编号，可能是菜名
        if (Regex("""^\d+[\.、)]""").containsMatchIn(originalLine)) {
            confidence += 0.15f
        }
        
        // 如果包含常见菜名结构（如"XX炒XX"、"XX汤"等），加分
        val dishPatterns = listOf(
            Regex("""[^，。]+[炒蒸煮炸烤炖烧煎焖]"""),  // 烹饪方式
            Regex("""[^，。]+[汤羹煲]"""),  // 汤类
            Regex("""[^，。]+[饭面粥]""")  // 主食
        )
        if (dishPatterns.any { it.containsMatchIn(name) }) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0f, 1f)
    }
    
    /**
     * 规范化菜名（用于去重和匹配）
     */
    private fun normalizeDishName(name: String): String {
        return name
            .replace(Regex("""\s+"""), "") // 移除所有空格
            .replace(Regex("""[（）()【】\[\]{}〈〉<>]"""), "") // 移除所有括号
            .replace(Regex("""[，。、·]"""), "") // 移除标点
            .lowercase()  // 转为小写（用于去重）
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
