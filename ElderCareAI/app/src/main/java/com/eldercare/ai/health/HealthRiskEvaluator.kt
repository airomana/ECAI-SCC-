package com.eldercare.ai.health

import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.ui.screens.menu.RiskLevel

/**
 * 健康风险评估引擎
 * 基于用户健康档案评估菜品风险等级
 */
class HealthRiskEvaluator {
    
    /**
     * 评估菜品风险
     */
    fun evaluateRisk(
        dishName: String,
        healthProfile: HealthProfile?
    ): HealthRiskResult {
        if (healthProfile == null || healthProfile.diseases.isEmpty()) {
            // 无健康档案，返回中等风险
            return HealthRiskResult(
                riskLevel = RiskLevel.MEDIUM,
                advice = "建议根据个人情况适量食用",
                reasons = emptyList()
            )
        }
        
        val diseases = healthProfile.diseases.map { it.lowercase() }
        val allergies = healthProfile.allergies.map { it.lowercase() }
        val dishLower = dishName.lowercase()
        
        val riskReasons = mutableListOf<String>()
        var riskScore = 0
        
        // 检查过敏
        allergies.forEach { allergy ->
            if (dishLower.contains(allergy) || containsAllergyKeyword(dishLower, allergy)) {
                riskReasons.add("您对「$allergy」过敏，此菜品可能含有相关成分")
                riskScore += 10
            }
        }
        
        // 检查疾病相关风险
        diseases.forEach { disease ->
            val diseaseRisk = evaluateDiseaseRisk(dishLower, disease)
            if (diseaseRisk.score > 0) {
                riskReasons.add(diseaseRisk.reason)
                riskScore += diseaseRisk.score
            }
        }
        
        // 通用高风险关键词检查
        val highRiskKeywords = listOf(
            "油炸", "炸", "油条", "油饼", "炸鸡", "炸鱼",
            "红烧", "糖醋", "蜜汁", "拔丝",
            "内脏", "肥肉", "五花肉", "肥肠",
            "腌制", "腊肉", "咸菜", "泡菜",
            "高糖", "甜品", "蛋糕", "巧克力"
        )
        
        highRiskKeywords.forEach { keyword ->
            if (dishLower.contains(keyword)) {
                riskReasons.add("此菜品可能含有「$keyword」，需注意")
                riskScore += 2
            }
        }
        
        // 通用低风险关键词（加分）
        val lowRiskKeywords = listOf(
            "清蒸", "白煮", "水煮", "清炒", "凉拌",
            "蔬菜", "青菜", "白菜", "萝卜", "冬瓜",
            "鱼", "虾", "鸡胸", "瘦肉"
        )
        
        var lowRiskBonus = 0
        lowRiskKeywords.forEach { keyword ->
            if (dishLower.contains(keyword)) {
                lowRiskBonus -= 1
            }
        }
        riskScore = (riskScore + lowRiskBonus).coerceAtLeast(0)
        
        // 根据风险分数确定等级
        val riskLevel = when {
            riskScore >= 8 -> RiskLevel.HIGH
            riskScore >= 4 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        
        // 生成建议
        val advice = generateAdvice(riskLevel, riskReasons, diseases)
        
        return HealthRiskResult(
            riskLevel = riskLevel,
            advice = advice,
            reasons = riskReasons
        )
    }
    
    private fun evaluateDiseaseRisk(dishName: String, disease: String): DiseaseRisk {
        return when {
            disease.contains("高血压") || disease.contains("高血脂") -> {
                when {
                    dishName.contains("油炸") || dishName.contains("炸") -> 
                        DiseaseRisk(5, "高血压/高血脂患者应避免油炸食品")
                    dishName.contains("肥肉") || dishName.contains("五花") -> 
                        DiseaseRisk(4, "高血压/高血脂患者应减少高脂肪食物")
                    dishName.contains("咸") || dishName.contains("腌制") -> 
                        DiseaseRisk(3, "高血压患者应控制盐分摄入")
                    else -> DiseaseRisk(0, "")
                }
            }
            disease.contains("糖尿病") -> {
                when {
                    dishName.contains("糖") || dishName.contains("甜") || dishName.contains("蜜") -> 
                        DiseaseRisk(6, "糖尿病患者应严格控制糖分摄入")
                    dishName.contains("米饭") || dishName.contains("面条") || dishName.contains("粥") -> 
                        DiseaseRisk(2, "糖尿病患者需控制主食摄入量")
                    else -> DiseaseRisk(0, "")
                }
            }
            disease.contains("痛风") -> {
                when {
                    dishName.contains("海鲜") || dishName.contains("虾") || dishName.contains("蟹") -> 
                        DiseaseRisk(5, "痛风患者应避免高嘌呤食物如海鲜")
                    dishName.contains("内脏") || dishName.contains("肝") || dishName.contains("肾") -> 
                        DiseaseRisk(5, "痛风患者应避免动物内脏")
                    dishName.contains("肉汤") || dishName.contains("浓汤") -> 
                        DiseaseRisk(3, "痛风患者应避免浓汤")
                    else -> DiseaseRisk(0, "")
                }
            }
            disease.contains("肾病") || disease.contains("肾") -> {
                when {
                    dishName.contains("咸") || dishName.contains("腌制") -> 
                        DiseaseRisk(4, "肾病患者应严格控制盐分")
                    dishName.contains("高蛋白") || dishName.contains("大量蛋白") -> 
                        DiseaseRisk(3, "肾病患者需控制蛋白质摄入")
                    else -> DiseaseRisk(0, "")
                }
            }
            else -> DiseaseRisk(0, "")
        }
    }
    
    private fun containsAllergyKeyword(dishName: String, allergy: String): Boolean {
        // 简单的过敏关键词匹配
        val allergyMap = mapOf(
            "花生" to listOf("花生", "花生酱", "花生油"),
            "海鲜" to listOf("虾", "蟹", "鱼", "贝", "海"),
            "牛奶" to listOf("牛奶", "乳", "奶"),
            "鸡蛋" to listOf("蛋", "鸡"),
            "大豆" to listOf("豆", "豆腐", "豆浆")
        )
        
        allergyMap[allergy]?.forEach { keyword ->
            if (dishName.contains(keyword)) return true
        }
        return false
    }
    
    private fun generateAdvice(
        riskLevel: RiskLevel,
        reasons: List<String>,
        diseases: List<String>
    ): String {
        return when (riskLevel) {
            RiskLevel.HIGH -> {
                if (reasons.isNotEmpty()) {
                    "⚠️ 此菜品可能不适合您，建议少吃或不吃。${reasons.first()}"
                } else {
                    "⚠️ 此菜品可能不适合您，建议少吃或不吃"
                }
            }
            RiskLevel.MEDIUM -> {
                "可以适量食用，但需注意控制分量"
            }
            RiskLevel.LOW -> {
                "✅ 此菜品比较适合您，可以放心食用"
            }
        }
    }
    
    private data class DiseaseRisk(
        val score: Int,
        val reason: String
    )
}

/**
 * 健康风险评估结果
 */
data class HealthRiskResult(
    val riskLevel: RiskLevel,
    val advice: String,
    val reasons: List<String>
)