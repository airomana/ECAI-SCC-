package com.eldercare.ai.health

import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.ui.screens.menu.RiskLevel

/**
 * 健康风险评估器
 * 根据菜品名称和个人健康档案，评估菜品的健康风险等级
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
        
        // 调试日志：开始评估
        android.util.Log.d("MenuScan", "HealthRisk开始评估菜品: $dishName")
        if (healthProfile != null) {
            android.util.Log.d("MenuScan", "HealthRisk用户档案: 疾病=${healthProfile.diseases}, 过敏=${healthProfile.allergies}")
        } else {
            android.util.Log.d("MenuScan", "HealthRisk用户档案: null")
        }
        
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
            android.util.Log.d("MenuScan", "HealthRisk开始个人档案评估，当前分数: $riskScore")
            val personalScore = evaluatePersonalHealthImpact(
                dishLower, 
                healthProfile, 
                riskReasons
            )
            riskScore += personalScore
            android.util.Log.d("MenuScan", "HealthRisk个人档案评估得分: $personalScore, 总分: $riskScore")
            
            // 过敏原一票否决（直接标记HIGH）
            val hasAllergy = hasAllergyMatch(dishLower, healthProfile.allergies)
            android.util.Log.d("MenuScan", "HealthRisk过敏检查: $hasAllergy")
            if (hasAllergy) {
                riskScore = 99  // 直接设为最高风险
                android.util.Log.d("MenuScan", "HealthRisk检测到过敏，设置风险分数为99")
            }
        } else {
            android.util.Log.d("MenuScan", "HealthRisk无个人档案，跳过个性化评估")
        }
        
        // 确保分数不为负
        riskScore = riskScore.coerceAtLeast(0)
        
        // ========== 第五步：风险等级转换 ==========
        val riskLevel = when {
            riskScore >= 10 -> RiskLevel.HIGH   // 高风险（红色）
            riskScore >= 4 -> RiskLevel.MEDIUM  // 中风险（黄色）
            else -> RiskLevel.LOW               // 低风险（绿色）
        }
        
        android.util.Log.d("MenuScan", "HealthRisk最终评估结果: 菜品=$dishName, 总分=$riskScore, 风险等级=$riskLevel, 原因数量=${riskReasons.size}")
        android.util.Log.d("MenuScan", "HealthRisk风险原因: ${riskReasons.joinToString("; ")}")
        
        // 生成建议
        val diseases = healthProfile?.diseases?.map { it.lowercase() } ?: emptyList()
        val restrictions = healthProfile?.dietRestrictions?.map { it.lowercase() } ?: emptyList()
        val advice = generateAdvice(riskLevel, riskReasons, diseases, restrictions, dishName)
        
        return HealthRiskResult(
            riskLevel = riskLevel,
            advice = advice,
            reasons = riskReasons
        )
    }
    
    /**
     * 评估烹饪工艺风险
     */
    private fun evaluateCookingMethod(dishName: String, reasons: MutableList<String>): Int {
        var score = 0
        
        // 高风险工艺（+4分）
        val highRiskMethods = listOf("炸", "油焖", "红烧", "糖醋", "拔丝", "酥", "脆")
        highRiskMethods.forEach { method ->
            if (dishName.contains(method)) {
                score += 4
                reasons.add("采用「$method」工艺，高油高糖高热量，应避免")
                return@forEach
            }
        }
        
        // 中风险工艺（+2分）
        val mediumRiskMethods = listOf("炒", "煎", "烤", "爆", "熘")
        mediumRiskMethods.forEach { method ->
            if (dishName.contains(method)) {
                score += 2
                reasons.add("采用「$method」工艺，隐形钠含量高或糊化程度高（升糖快）")
                return@forEach
            }
        }
        
        return score
    }
    
    /**
     * 评估食材风险
     */
    private fun evaluateIngredients(dishName: String, reasons: MutableList<String>): Int {
        var score = 0
        
        // 高风险食材（+3分）
        val highRiskIngredients = listOf("肥肉", "五花肉", "肥肠", "猪油", "奶油", "黄油", "芝士")
        highRiskIngredients.forEach { ingredient ->
            if (dishName.contains(ingredient)) {
                score += 3
                reasons.add("含高饱和脂肪食材「$ingredient」，增加心血管负担")
                return@forEach
            }
        }
        
        // 中风险食材（+1分）
        val mediumRiskIngredients = listOf("肉", "蛋", "内脏", "海鲜", "豆制品")
        mediumRiskIngredients.forEach { ingredient ->
            if (dishName.contains(ingredient)) {
                score += 1
                reasons.add("含「$ingredient」，需适量控制")
                return@forEach
            }
        }
        
        return score
    }
    
    /**
     * 评估组合效应
     */
    private fun evaluateSynergyEffects(dishName: String, reasons: MutableList<String>): Int {
        var score = 0
        
        // 油腻+高糖组合（+3分）
        if (dishName.contains("糖") && (dishName.contains("油") || dishName.contains("炸"))) {
            score += 3
            reasons.add("高糖+高油组合，双重代谢负担")
        }
        
        // 重口味+高脂组合（+2分）
        if ((dishName.contains("辣") || dishName.contains("麻")) && dishName.contains("肥")) {
            score += 2
            reasons.add("重口味+高脂组合，刺激心血管")
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
        android.util.Log.d("MenuScan", "HealthRisk评估个人健康影响，菜品: $dishName, 疾病: $diseases")
        
        diseases.forEach { disease ->
            android.util.Log.d("MenuScan", "HealthRisk检查疾病: $disease")
            when {
                // 糖尿病：核心监控升糖指数(GI) - 全面覆盖高糖、高淀粉、高GI食材
                disease.contains("糖尿病") || disease.contains("血糖") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到糖尿病/血糖相关疾病")
                    val diabetesKeywords = listOf(
                        // 直接糖类
                        "甜", "糖", "蜜", "冰糖", "白糖", "红糖", "蜂蜜", "糖浆",
                        // 高淀粉主食
                        "淀粉", "粥", "米饭", "糯米", "年糕", "汤圆", "粽子", "米糕",
                        // 高糖调料和做法
                        "拔丝", "糖醋", "蜜汁", "糖水", "糖渍", "甜面酱",
                        // 高糖菜品
                        "鱼香", "宫保", "咕咾", "糖醋", "甜豆", "糖藕",
                        // 隐形糖分
                        "豆瓣酱", "耗油", "生抽", "老抽", "料酒", "黄酱",
                        // 高糖水果
                        "葡萄", "荔枝", "龙眼", "芒果", "香蕉", "西瓜",
                        // 高糖饮料
                        "果汁", "汽水", "奶茶", "可乐"
                    )
                    var matched = false
                    diabetesKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 糖尿病患者：此菜品升糖指数高，应严格控制")
                            android.util.Log.d("MenuScan", "HealthRisk糖尿病匹配关键词: $keyword, 得分+8, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 高血压：核心监控钠离子(Na+) - 全面覆盖高盐、腌制、重口味食材
                disease.contains("高血压") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到高血压相关疾病")
                    val hypertensionKeywords = listOf(
                        // 腌制类
                        "腌", "咸", "腊", "酱", "熏", "腐乳", "泡菜", "咸菜", "榨菜", "咸肉", "火腿", "香肠", "腊肉", "腊肠",
                        // 重口味调料
                        "盐", "酱油", "老抽", "生抽", "豆瓣酱", "黄酱", "甜面酱", "蚝油", "豆豉", "虾酱",
                        // 重口味做法
                        "重口味", "麻辣", "酸辣", "香辣", "剁椒", "泡椒", "酸菜",
                        // 重口味食材
                        "麻", "辣", "花椒", "辣椒", "芥末", "蒜", "姜", "葱", "洋葱",
                        // 高钠加工食品
                        "火腿", "培根", "香肠", "腊肉", "咸鱼", "虾米", "紫菜", "海带",
                        // 隐形高钠
                        "味精", "鸡精", "高汤", "浓汤", "火锅", "烧烤", "卤味"
                    )
                    var matched = false
                    hypertensionKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 6
                            reasons.add("⚠️ 高血压患者：此菜品钠含量高，应严格控制")
                            android.util.Log.d("MenuScan", "HealthRisk高血压匹配关键词: $keyword, 得分+6, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 高血脂/冠心病：核心监控饱和脂肪与胆固醇 - 全面覆盖高脂肪、高胆固醇食材
                disease.contains("高血脂") || disease.contains("冠心病") || disease.contains("心脏病") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到高血脂/冠心病相关疾病")
                    val lipidKeywords = listOf(
                        // 高脂肪肉类
                        "肥", "五花肉", "肥肉", "肥肠", "猪油", "牛油", "羊油", "黄油", "奶油",
                        // 动物内脏
                        "内脏", "肝", "心", "肺", "肾", "肚", "肠", "脑", "骨髓", "血", "猪血", "鸭血",
                        // 高脂肪做法
                        "炸", "油炸", "油煎", "油焖", "红烧", "爆炒", "酥", "脆", "烤", "熏",
                        // 高脂肪食材
                        "油", "花生", "芝麻", "核桃", "杏仁", "腰果", "瓜子", "松子",
                        // 高胆固醇
                        "蛋黄", "鱼籽", "虾籽", "蟹黄", "鱼卵", "海鲜", "虾", "蟹", "贝", "鱿鱼",
                        // 隐形高脂肪
                        "沙拉酱", "蛋黄酱", "芝士", "奶酪", "巧克力", "奶油", "黄油"
                    )
                    var matched = false
                    lipidKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 高血脂/冠心病患者：此菜品饱和脂肪和胆固醇含量高")
                            android.util.Log.d("MenuScan", "HealthRisk高血脂匹配关键词: $keyword, 得分+8, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 痛风/高尿酸：核心监控嘌呤含量 - 全面覆盖高嘌呤食材
                disease.contains("痛风") || disease.contains("尿酸") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到痛风/尿酸相关疾病")
                    val goutKeywords = listOf(
                        // 海鲜类
                        "海鲜", "虾", "蟹", "贝", "蛤蜊", "扇贝", "牡蛎", "海螺", "鱿鱼", "章鱼", "海参", "鱼", "带鱼", "黄花鱼", "鲤鱼", "鲫鱼",
                        // 内脏类
                        "肝", "肾", "心", "肺", "肚", "肠", "脑", "脾", "胰", "猪肝", "牛肝", "鸡肝", "鸭肝", "猪肾", "牛肾",
                        // 高嘌呤蔬菜
                        "豆芽", "芦笋", "菠菜", "紫菜", "海带", "香菇", "金针菇", "草菇", "蘑菇",
                        // 肉汤类
                        "浓汤", "肉汤", "鸡汤", "鸭汤", "排骨汤", "牛骨汤", "羊汤", "火锅汤", "高汤",
                        // 豆制品
                        "豆", "豆腐", "豆浆", "豆干", "腐竹", "豆腐皮", "黄豆", "黑豆", "绿豆", "红豆",
                        // 其他高嘌呤
                        "酵母", "啤酒", "白酒", "酒精", "鸡精", "味精", "鱼籽", "虾籽", "蟹黄"
                    )
                    var matched = false
                    goutKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 痛风患者：此菜品嘌呤含量高，易引发痛风发作")
                            android.util.Log.d("MenuScan", "HealthRisk痛风匹配关键词: $keyword, 得分+8, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 慢性肾病：减轻肾脏代谢负担 - 全面控制蛋白质、钠、钾、磷摄入
                disease.contains("肾病") || disease.contains("肾") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到肾病相关疾病")
                    val kidneyKeywords = listOf(
                        // 高盐类
                        "盐", "咸", "腌", "腊", "酱", "熏", "泡菜", "咸菜", "榨菜", "咸肉", "火腿", "香肠",
                        // 高蛋白类
                        "蛋白", "鸡蛋", "鸭蛋", "鹌鹑蛋", "肉", "鱼", "虾", "蟹", "贝", "海鲜",
                        // 高钾类
                        "香蕉", "橙子", "橘子", "柚子", "猕猴桃", "菠菜", "土豆", "红薯", "山药", "番茄",
                        // 高磷类
                        "奶", "牛奶", "酸奶", "奶酪", "芝士", "坚果", "花生", "核桃", "杏仁", "瓜子",
                        // 加工食品
                        "火腿", "培根", "香肠", "腊肉", "罐头", "速食", "方便面", "薯片",
                        // 调料
                        "酱油", "老抽", "生抽", "豆瓣酱", "黄酱", "蚝油", "豆豉", "虾酱",
                        // 其他
                        "浓汤", "高汤", "火锅", "烧烤", "油炸", "辛辣", "刺激"
                    )
                    var matched = false
                    kidneyKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 6
                            reasons.add("⚠️ 肾病患者：此菜品加重肾脏代谢负担")
                            android.util.Log.d("MenuScan", "HealthRisk肾病匹配关键词: $keyword, 得分+6, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 脂肪肝：控制脂肪和糖分摄入，减轻肝脏负担
                disease.contains("脂肪肝") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到脂肪肝相关疾病")
                    val fattyLiverKeywords = listOf(
                        // 高脂肪肉类
                        "肥", "五花肉", "肥肉", "肥肠", "猪油", "牛油", "羊油", "黄油", "奶油",
                        // 高脂肪做法
                        "炸", "油炸", "油煎", "油焖", "红烧", "爆炒", "酥", "脆", "烤", "熏",
                        // 高糖类
                        "甜", "糖", "蜜", "冰糖", "白糖", "红糖", "蜂蜜", "糖浆",
                        // 高糖菜品
                        "拔丝", "糖醋", "蜜汁", "糖水", "糖渍", "甜面酱", "鱼香", "宫保",
                        // 酒精类
                        "酒", "白酒", "啤酒", "红酒", "黄酒", "料酒", "酒精",
                        // 高胆固醇
                        "内脏", "肝", "心", "肺", "肾", "肚", "肠", "脑", "蛋黄", "鱼籽",
                        // 隐形高脂肪
                        "沙拉酱", "蛋黄酱", "芝士", "奶酪", "巧克力", "奶油", "黄油",
                        // 其他
                        "坚果", "花生", "核桃", "杏仁", "腰果", "瓜子", "松子"
                    )
                    var matched = false
                    fattyLiverKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 脂肪肝患者：此菜品脂肪或糖分含量高，加重肝脏负担")
                            android.util.Log.d("MenuScan", "HealthRisk脂肪肝匹配关键词: $keyword, 得分+8, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 胃病：避免刺激性食物，保护胃黏膜
                disease.contains("胃病") || disease.contains("胃炎") || disease.contains("胃溃疡") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到胃病相关疾病")
                    val stomachKeywords = listOf(
                        // 刺激性调料
                        "辣", "麻", "花椒", "辣椒", "芥末", "蒜", "姜", "葱", "洋葱",
                        // 重口味做法
                        "麻辣", "酸辣", "香辣", "剁椒", "泡椒", "酸菜", "泡菜",
                        // 酸性食物
                        "酸", "醋", "酸菜", "泡菜", "柠檬", "山楂", "酸梅",
                        // 油腻食物
                        "油", "炸", "油炸", "油煎", "油焖", "肥", "肥肉", "五花肉",
                        // 生冷食物
                        "生", "冷", "凉拌", "沙拉", "冰", "冻",
                        // 难消化食物
                        "硬", "脆", "坚果", "花生", "核桃", "杏仁", "瓜子",
                        // 酒精类
                        "酒", "白酒", "啤酒", "红酒", "黄酒", "料酒", "酒精",
                        // 其他刺激
                        "浓茶", "咖啡", "碳酸", "汽水", "刺激"
                    )
                    var matched = false
                    stomachKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 6
                            reasons.add("⚠️ 胃病患者：此菜品刺激性较强，可能损伤胃黏膜")
                            android.util.Log.d("MenuScan", "HealthRisk胃病匹配关键词: $keyword, 得分+6, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 胆囊疾病：控制脂肪摄入，避免胆囊刺激
                disease.contains("胆囊") || disease.contains("胆结石") || disease.contains("胆病") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到胆囊相关疾病")
                    val gallbladderKeywords = listOf(
                        // 高脂肪食材
                        "肥", "肥肉", "五花肉", "肥肠", "猪油", "牛油", "羊油", "黄油", "奶油",
                        // 高脂肪做法
                        "炸", "油炸", "油煎", "油焖", "红烧", "爆炒", "酥", "脆", "烤", "熏",
                        // 油腻调料
                        "油", "沙拉酱", "蛋黄酱", "芝士", "奶酪", "巧克力",
                        // 高胆固醇
                        "内脏", "肝", "心", "肺", "肾", "肚", "肠", "脑", "蛋黄", "鱼籽", "虾籽", "蟹黄",
                        // 刺激性食物
                        "辣", "麻", "花椒", "辣椒", "芥末", "蒜", "姜", "葱", "洋葱",
                        // 其他
                        "坚果", "花生", "核桃", "杏仁", "腰果", "瓜子", "松子"
                    )
                    var matched = false
                    gallbladderKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 胆囊疾病患者：此菜品脂肪含量高，可能诱发胆囊疼痛")
                            android.util.Log.d("MenuScan", "HealthRisk胆囊匹配关键词: $keyword, 得分+8, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 肠道疾病：避免刺激性食物，保护肠道
                disease.contains("肠炎") || disease.contains("肠病") || disease.contains("腹泻") || disease.contains("便秘") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到肠道相关疾病")
                    val intestinalKeywords = listOf(
                        // 刺激性调料
                        "辣", "麻", "花椒", "辣椒", "芥末", "蒜", "姜", "葱", "洋葱",
                        // 重口味做法
                        "麻辣", "酸辣", "香辣", "剁椒", "泡椒", "酸菜", "泡菜",
                        // 油腻食物
                        "油", "炸", "油炸", "油煎", "油焖", "肥", "肥肉", "五花肉",
                        // 生冷食物
                        "生", "冷", "凉拌", "沙拉", "冰", "冻",
                        // 高纤维食物（便秘时需要，腹泻时避免）
                        "粗粮", "纤维", "芹菜", "韭菜", "豆类", "薯类",
                        // 酒精类
                        "酒", "白酒", "啤酒", "红酒", "黄酒", "料酒", "酒精",
                        // 其他刺激
                        "浓茶", "咖啡", "碳酸", "汽水", "刺激"
                    )
                    var matched = false
                    intestinalKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 6
                            reasons.add("⚠️ 肠道疾病患者：此菜品可能刺激肠道，加重症状")
                            android.util.Log.d("MenuScan", "HealthRisk肠道匹配关键词: $keyword, 得分+6, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                
                // 肝病：全面保护肝脏功能
                disease.contains("肝病") || disease.contains("肝炎") || disease.contains("肝硬化") -> {
                    android.util.Log.d("MenuScan", "HealthRisk检测到肝病相关疾病")
                    val liverKeywords = listOf(
                        // 酒精类（绝对禁忌）
                        "酒", "白酒", "啤酒", "红酒", "黄酒", "料酒", "酒精",
                        // 高脂肪食物
                        "肥", "肥肉", "五花肉", "肥肠", "猪油", "牛油", "羊油", "黄油", "奶油",
                        // 高脂肪做法
                        "炸", "油炸", "油煎", "油焖", "红烧", "爆炒", "酥", "脆", "烤", "熏",
                        // 高糖食物
                        "甜", "糖", "蜜", "冰糖", "白糖", "红糖", "蜂蜜", "糖浆",
                        // 高糖菜品
                        "拔丝", "糖醋", "蜜汁", "糖水", "糖渍", "甜面酱", "鱼香", "宫保",
                        // 高胆固醇
                        "内脏", "肝", "心", "肺", "肾", "肚", "肠", "脑", "蛋黄", "鱼籽",
                        // 隐形高脂肪
                        "沙拉酱", "蛋黄酱", "芝士", "奶酪", "巧克力", "奶油", "黄油",
                        // 其他
                        "坚果", "花生", "核桃", "杏仁", "腰果", "瓜子", "松子"
                    )
                    var matched = false
                    liverKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 8
                            reasons.add("⚠️ 肝病患者：此菜品加重肝脏负担，应严格避免")
                            android.util.Log.d("MenuScan", "HealthRisk肝病匹配关键词: $keyword, 得分+8, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    if (matched) return@forEach
                }
                else -> {
                    android.util.Log.d("MenuScan", "HealthRisk疾病 $disease 未匹配到任何关键词，使用通用匹配")
                    // 通用疾病匹配：对于未知的疾病，使用保守的匹配策略
                    val generalHighRiskKeywords = listOf(
                        // 通用高风险做法
                        "炸", "油炸", "油煎", "油焖", "红烧", "爆炒", "酥", "脆", "烤", "熏",
                        // 通用高风险食材
                        "肥", "肥肉", "五花肉", "肥肠", "内脏", "肝", "心", "肺", "肾", "肚", "肠", "脑",
                        // 通用高风险调料
                        "辣", "麻", "花椒", "辣椒", "芥末", "蒜", "姜", "葱", "洋葱",
                        // 通用高风险口味
                        "咸", "甜", "糖", "蜜", "盐", "酱", "腌", "腊", "熏",
                        // 通用高风险饮品
                        "酒", "白酒", "啤酒", "红酒", "黄酒", "料酒", "酒精"
                    )
                    
                    var matched = false
                    generalHighRiskKeywords.forEach { keyword ->
                        if (dishName.contains(keyword)) {
                            score += 4  // 通用匹配给予中等分数
                            reasons.add("⚠️ $disease 患者：此菜品可能不适合您的健康状况，建议谨慎食用")
                            android.util.Log.d("MenuScan", "HealthRisk通用匹配关键词: $keyword, 得分+4, 总分: $score")
                            matched = true
                            return@forEach
                        }
                    }
                    
                    if (!matched) {
                        android.util.Log.d("MenuScan", "HealthRisk疾病 $disease 未匹配到任何通用关键词，给予基础风险评分")
                        // 对于未知疾病，如果菜名包含任何烹饪工艺，都给予基础风险评分
                        val cookingMethods = listOf("炒", "煮", "蒸", "炖", "焖", "烧", "烤", "煎", "炸", "凉拌")
                        cookingMethods.forEach { method ->
                            if (dishName.contains(method)) {
                                score += 2
                                reasons.add("⚠️ $disease 患者：建议清淡饮食，控制烹饪方式")
                                android.util.Log.d("MenuScan", "HealthRisk通用烹饪匹配: $method, 得分+2, 总分: $score")
                                return@forEach
                            }
                        }
                    }
                }
            }
        }
        
        android.util.Log.d("MenuScan", "HealthRisk个人健康影响评估完成，最终得分: $score")
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
        restrictions: List<String>,
        dishName: String
    ): String {
        return when (riskLevel) {
            RiskLevel.HIGH -> {
                val mainReason = reasons.firstOrNull() ?: "不适合您的健康状况"
                "❌ 极不推荐：$mainReason，强烈建议避免食用！"
            }
            RiskLevel.MEDIUM -> {
                val mainReason = reasons.firstOrNull()
                if (mainReason != null) {
                    "⚠️ 不太推荐：$mainReason，建议少吃或不吃"
                } else {
                    "⚠️ 不太推荐：建议控制分量，少吃为好"
                }
            }
            RiskLevel.LOW -> {
                val mainReason = reasons.firstOrNull()
                if (mainReason != null) {
                    "✅ 可以食用：$mainReason，适量即可"
                } else {
                    "✅ 推荐：此菜品相对安全，可以放心食用"
                }
            }
        }
    }
}

/**
 * 风险评估结果
 */
data class HealthRiskResult(
    val riskLevel: RiskLevel,
    val advice: String,
    val reasons: List<String>
)
