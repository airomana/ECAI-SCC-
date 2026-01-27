package com.eldercare.ai.health

import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.ui.screens.menu.RiskLevel

/**
 * 老年人智慧膳食风险评估引擎 V2.0
 * 采用分层评估架构：烹饪工艺 + 食材属性 + 组合效应 + 个性化疾病耦合
 */
class HealthRiskEvaluator {
    
    /**
     * 评估菜品风险
     */
    fun evaluateRisk(
        dishName: String,
        healthProfile: HealthProfile?
    ): HealthRiskResult {
        val dishLower = dishName.lowercase()
        val riskReasons = mutableListOf<String>()
        var riskScore = 0
        
        // ========== 第一步：烹饪工艺分级评估 ==========
        val cookingScore = evaluateCookingMethod(dishLower, riskReasons)
        riskScore += cookingScore
        
        // ========== 第二步：食材分类分级评估 ==========
        val ingredientScore = evaluateIngredients(dishLower, riskReasons)
        riskScore += ingredientScore
        
        // ========== 第三步：组合效应评分（负面协同） ==========
        val synergyScore = evaluateSynergyEffects(dishLower, riskReasons)
        riskScore += synergyScore
        
        // ========== 第四步：个性化健康档案深度耦合 ==========
        if (healthProfile != null) {
            val personalScore = evaluatePersonalHealthImpact(
                dishLower, 
                healthProfile, 
                riskReasons
            )
            riskScore += personalScore
            
            // 过敏原一票否决（直接标记HIGH）
            if (hasAllergyMatch(dishLower, healthProfile.allergies)) {
                riskScore = 99  // 直接设为最高风险
            }
        }
        
        // 确保分数不为负
        riskScore = riskScore.coerceAtLeast(0)
        
        // ========== 第五步：风险等级转换 ==========
        val riskLevel = when {
            riskScore >= 16 -> RiskLevel.HIGH   // 高风险（红色）
            riskScore >= 8 -> RiskLevel.MEDIUM  // 中风险（黄色）
            else -> RiskLevel.LOW               // 低风险（绿色）
        }
        
        // 生成建议
        val diseases = healthProfile?.diseases?.map { it.lowercase() } ?: emptyList()
        val advice = generateAdvice(riskLevel, riskReasons, diseases, dishName)
        
        return HealthRiskResult(
            riskLevel = riskLevel,
            advice = advice,
            reasons = riskReasons
        )
    }
    
    /**
     * 烹饪工艺分级评估
     * 按优先级匹配，只计算最高风险等级的工艺
     */
    private fun evaluateCookingMethod(dishName: String, reasons: MutableList<String>): Int {
        // 极高风险：+6分（最高优先级）
        val extremeRiskKeywords = listOf(
            "炸", "油炸", "深炸", "复炸", "干煸", "爆鱼", "过油"
        )
        extremeRiskKeywords.forEach { keyword ->
            if (dishName.contains(keyword)) {
                reasons.add("采用「$keyword」工艺，油脂反复高温产生反式脂肪酸")
                return 6
            }
        }
        
        // 高风险：+4分
        val highRiskKeywords = listOf(
            "红烧", "糖醋", "蜜汁", "拔丝", "油焖", "干锅", "烧烤", "熏"
        )
        highRiskKeywords.forEach { keyword ->
            if (dishName.contains(keyword)) {
                reasons.add("采用「$keyword」工艺，伴随大量糖、盐及美拉德反应产物")
                return 4
            }
        }
        
        // 中风险：+2分
        val mediumRiskKeywords = listOf(
            "卤", "酱", "腌", "烩", "勾芡", "煎", "炒", "烧"
        )
        mediumRiskKeywords.forEach { keyword ->
            if (dishName.contains(keyword)) {
                reasons.add("采用「$keyword」工艺，隐形钠含量高或糊化程度高（升糖快）")
                return 2
            }
        }
        
        // 低风险：-2分（减分）
        val lowRiskKeywords = listOf(
            "清蒸", "白灼", "水煮", "凉拌", "清炒", "炖"
        )
        lowRiskKeywords.forEach { keyword ->
            if (dishName.contains(keyword)) {
                reasons.add("采用「$keyword」工艺，保留营养，油脂摄入可控")
                return -2
            }
        }
        
        return 0
    }
    
    /**
     * 食材分类分级评估
     * 可以累加多个食材风险
     */
    private fun evaluateIngredients(dishName: String, reasons: MutableList<String>): Int {
        var score = 0
        val matchedKeywords = mutableSetOf<String>()
        
        // 高脂动物：+5分
        val highFatKeywords = listOf(
            "肥肉", "五花肉", "猪蹄", "皮蛋", "黄油", "猪油", "肥肠", 
            "脑", "骨髓", "肥牛", "肥羊", "鸭皮", "鸡皮"
        )
        highFatKeywords.forEach { keyword ->
            if (dishName.contains(keyword) && 
                !dishName.contains("瘦肉") && 
                !dishName.contains("去皮") &&
                keyword !in matchedKeywords) {
                score += 5
                matchedKeywords.add(keyword)
                reasons.add("含有「$keyword」，高饱和脂肪，对心血管不利")
            }
        }
        
        // 加工肉类：+4分
        val processedMeatKeywords = listOf(
            "腊肉", "香肠", "火腿", "培根", "午餐肉", "肉丸", "肉松", 
            "肉干", "熏肉", "咸肉", "腌肉", "腊肠"
        )
        processedMeatKeywords.forEach { keyword ->
            if (dishName.contains(keyword) && keyword !in matchedKeywords) {
                score += 4
                matchedKeywords.add(keyword)
                reasons.add("含有「$keyword」，加工肉类含亚硝酸盐和大量钠")
            }
        }
        
        // 高嘌呤/内脏：+4分
        val purineKeywords = listOf(
            "肝", "肾", "心", "肚", "腰子", "浓肉汤", "海鲜火锅底",
            "鸭血", "猪血", "血", "脑", "骨髓", "鱼籽", "虾籽"
        )
        purineKeywords.forEach { keyword ->
            if (dishName.contains(keyword) && keyword !in matchedKeywords) {
                score += 4
                matchedKeywords.add(keyword)
                reasons.add("含有「$keyword」，高嘌呤食物，易引发痛风")
            }
        }
        
        // 精制碳水：+2分
        val refinedCarbKeywords = listOf(
            "糯米", "油条", "白面饼", "粥", "白米饭", "白面条", 
            "精面", "白面包", "年糕", "汤圆", "粽子"
        )
        refinedCarbKeywords.forEach { keyword ->
            if (dishName.contains(keyword) && 
                !dishName.contains("燕麦") && 
                !dishName.contains("糙米") && 
                !dishName.contains("全麦") &&
                keyword !in matchedKeywords) {
                score += 2
                matchedKeywords.add(keyword)
                reasons.add("含有「$keyword」，精制碳水升糖指数高")
            }
        }
        
        return score
    }
    
    /**
     * 组合效应评分（负面协同）
     */
    private fun evaluateSynergyEffects(dishName: String, reasons: MutableList<String>): Int {
        var score = 0
        
        // 油+糖 叠加：+3分
        val oilKeywords = listOf("炸", "油炸", "干煸", "过油", "油焖")
        val sugarKeywords = listOf("糖", "甜", "蜜", "糖醋", "拔丝", "蜜汁")
        
        val hasOil = oilKeywords.any { dishName.contains(it) }
        val hasSugar = sugarKeywords.any { dishName.contains(it) }
        
        if (hasOil && hasSugar) {
            score += 3
            reasons.add("⚠️ 油+糖叠加：油炸+糖分组合，热量极高且易引发炎症")
        }
        
        // 盐+脂 叠加：+2分
        val saltKeywords = listOf("腌", "腊", "酱", "咸", "熏", "腐乳", "泡菜")
        val fatKeywords = listOf("肥肉", "五花", "内脏", "肥肠", "脑")
        
        val hasSalt = saltKeywords.any { dishName.contains(it) }
        val hasFat = fatKeywords.any { dishName.contains(it) }
        
        if (hasSalt && hasFat) {
            score += 2
            reasons.add("⚠️ 盐+脂叠加：高盐+高脂组合，加重心血管负担")
        }
        
        // 淀粉+油脂 叠加：+2分
        val starchKeywords = listOf("土豆", "山药", "糯米", "年糕", "勾芡", "淀粉")
        val oilKeywords2 = listOf("炸", "油", "干煸", "油焖", "红烧")
        
        val hasStarch = starchKeywords.any { dishName.contains(it) }
        val hasOil2 = oilKeywords2.any { dishName.contains(it) }
        
        if (hasStarch && hasOil2) {
            score += 2
            reasons.add("⚠️ 淀粉+油脂叠加：高淀粉食材吸油，热量密度极高")
        }
        
        return score
    }
    
    /**
     * 个性化健康档案深度耦合
     */
    private fun evaluatePersonalHealthImpact(
        dishName: String,
        healthProfile: HealthProfile,
        reasons: MutableList<String>
    ): Int {
        var score = 0
        val diseases = healthProfile.diseases.map { it.lowercase() }
        
        diseases.forEach { disease ->
            when {
                // 糖尿病：核心监控升糖指数(GI)
                disease.contains("糖尿病") || disease.contains("血糖") -> {
                    val diabetesKeywords = listOf(
                        "甜", "糖", "蜜", "淀粉", "粥", "勾芡", "糯米",
                        "拔丝", "糖醋", "蜜汁", "年糕", "汤圆", "粽子"
                    )
                    diabetesKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 糖尿病患者：此菜品升糖指数高，应严格控制")
                            return@forEach
                        }
                    }
                }
                
                // 高血压：核心监控钠离子(Na+)
                disease.contains("高血压") -> {
                    val hypertensionKeywords = listOf(
                        "腌", "咸", "腊", "酱", "熏", "腐乳", "泡菜",
                        "咸菜", "榨菜", "咸肉", "火腿", "香肠"
                    )
                    hypertensionKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 6
                            reasons.add("⚠️ 高血压患者：此菜品钠含量高，应严格控制")
                            return@forEach
                        }
                    }
                }
                
                // 高血脂/冠心病：核心监控饱和脂肪与胆固醇
                disease.contains("高血脂") || disease.contains("冠心病") || disease.contains("心脏病") -> {
                    val lipidKeywords = listOf(
                        "炸", "肥", "皮", "油", "内脏", "奶油", "黄油",
                        "五花", "肥肉", "肥肠", "脑", "骨髓"
                    )
                    lipidKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 高血脂/冠心病患者：此菜品饱和脂肪和胆固醇含量高")
                            return@forEach
                        }
                    }
                }
                
                // 痛风/高尿酸：核心监控嘌呤含量
                disease.contains("痛风") || disease.contains("尿酸") -> {
                    val goutKeywords = listOf(
                        "海鲜", "虾", "蟹", "肝", "肾", "浓肉汤", "豆芽",
                        "鸭血", "猪血", "内脏", "鱼籽", "虾籽", "贝类"
                    )
                    goutKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 痛风患者：此菜品嘌呤含量高，易引发痛风发作")
                            return@forEach
                        }
                    }
                }
                
                // 慢性肾病：减轻肾脏代谢负担
                disease.contains("肾病") || disease.contains("肾") -> {
                    val kidneyKeywords = listOf(
                        "盐", "腌", "高蛋白", "大量蛋白", "咸", "腊", "酱"
                    )
                    kidneyKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 6
                            reasons.add("⚠️ 肾病患者：此菜品加重肾脏代谢负担")
                            return@forEach
                        }
                    }
                }
            }
        }
        
        return score
    }
    
    /**
     * 检查过敏原匹配（一票否决）
     */
    private fun hasAllergyMatch(dishName: String, allergies: List<String>): Boolean {
        val allergyMap = mapOf(
            "花生" to listOf("花生", "花生酱", "花生油", "花生米"),
            "海鲜" to listOf("虾", "蟹", "鱼", "贝", "海", "鱿鱼", "章鱼", "海参"),
            "牛奶" to listOf("牛奶", "乳", "奶", "奶油", "奶酪", "芝士"),
            "鸡蛋" to listOf("蛋", "鸡蛋", "鸭蛋", "鹌鹑蛋"),
            "大豆" to listOf("豆", "豆腐", "豆浆", "豆制品", "黄豆", "黑豆"),
            "小麦" to listOf("面", "面粉", "小麦", "麸质", "面包", "面条"),
            "坚果" to listOf("核桃", "杏仁", "腰果", "榛子", "开心果"),
            "芒果" to listOf("芒果"),
            "菠萝" to listOf("菠萝", "凤梨")
        )
        
        allergies.forEach { allergy ->
            val allergyLower = allergy.lowercase()
            // 直接匹配
            if (dishName.contains(allergyLower)) {
                return true
            }
            // 通过映射表匹配
            allergyMap[allergyLower]?.forEach { keyword ->
                if (dishName.contains(keyword)) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * 生成个性化建议
     */
    private fun generateAdvice(
        riskLevel: RiskLevel,
        reasons: List<String>,
        diseases: List<String>,
        dishName: String
    ): String {
        return when (riskLevel) {
            RiskLevel.HIGH -> {
                val diseaseHint = if (diseases.isNotEmpty()) {
                    "可能加重您的${diseases.first()}风险"
                } else {
                    "对老年人健康不利"
                }
                if (reasons.isNotEmpty()) {
                    "❌ 极不推荐！$diseaseHint。${reasons.first()}"
                } else {
                    "❌ 极不推荐！$diseaseHint"
                }
            }
            RiskLevel.MEDIUM -> {
                "⚠️ 偶尔尝试，建议搭配大量蔬菜并减半食用"
            }
            RiskLevel.LOW -> {
                "✅ 较为健康，适合老年人食用"
            }
        }
    }
}

/**
 * 健康风险评估结果
 */
data class HealthRiskResult(
    val riskLevel: RiskLevel,
    val advice: String,
    val reasons: List<String>
)
