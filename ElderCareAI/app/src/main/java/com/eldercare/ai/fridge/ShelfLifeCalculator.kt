package com.eldercare.ai.fridge

import java.util.concurrent.TimeUnit

/**
 * 保质期计算器
 * 根据食材类别和存储条件推算保质期
 */
object ShelfLifeCalculator {
    
    /**
     * 食材保质期规则（冷藏条件下，单位：天）
     */
    private val shelfLifeRules = mapOf(
        // 蔬菜类
        "青菜" to 3,
        "白菜" to 7,
        "萝卜" to 14,
        "土豆" to 30,
        "西红柿" to 7,
        "黄瓜" to 5,
        "茄子" to 5,
        "青椒" to 7,
        "芹菜" to 7,
        "菠菜" to 3,
        "生菜" to 3,
        
        // 水果类
        "苹果" to 14,
        "香蕉" to 5,
        "橙子" to 14,
        "葡萄" to 7,
        "西瓜" to 3,
        "梨" to 14,
        "桃子" to 5,
        "草莓" to 3,
        
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
        "酸奶" to 14,
        "奶酪" to 30,
        
        // 豆制品
        "豆腐" to 3,
        "豆干" to 7,
        
        // 其他
        "米饭" to 2,
        "面条" to 2
    )
    
    /**
     * 类别默认保质期（天）
     */
    private val categoryDefaultShelfLife = mapOf(
        "蔬菜" to 5,
        "水果" to 7,
        "肉类" to 3,
        "蛋奶" to 14,
        "豆制品" to 5,
        "主食" to 2,
        "其他" to 3
    )
    
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
        // 优先使用具体食材的规则
        shelfLifeRules[foodName]?.let { return it }
        
        // 使用类别默认规则
        categoryDefaultShelfLife[category]?.let { return it }
        
        // 默认3天
        return 3
    }
    
    /**
     * 计算食材状态
     */
    fun calculateFoodStatus(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): FoodStatus {
        val remainingDays = TimeUnit.MILLISECONDS.toDays(expiryTime - currentTime)
        
        return when {
            remainingDays < 0 -> FoodStatus.EXPIRED
            remainingDays <= 2 -> FoodStatus.EXPIRING_SOON
            else -> FoodStatus.FRESH
        }
    }
    
    /**
     * 获取剩余天数
     */
    fun getRemainingDays(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): Long {
        return TimeUnit.MILLISECONDS.toDays(expiryTime - currentTime)
    }
    
    /**
     * 获取过期天数（已过期的情况）
     */
    fun getExpiredDays(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): Long {
        return TimeUnit.MILLISECONDS.toDays(currentTime - expiryTime)
    }
    
    /**
     * 生成状态描述文本
     */
    fun getStatusText(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): String {
        val status = calculateFoodStatus(expiryTime, currentTime)
        
        return when (status) {
            FoodStatus.EXPIRED -> {
                val days = getExpiredDays(expiryTime, currentTime)
                "已经过期${days}天了"
            }
            FoodStatus.EXPIRING_SOON -> {
                val days = getRemainingDays(expiryTime, currentTime)
                if (days == 0L) "今天就要过期了" else "还有${days}天就过期了"
            }
            FoodStatus.FRESH -> {
                val days = getRemainingDays(expiryTime, currentTime)
                "还能放${days}天"
            }
        }
    }
    
    /**
     * 生成建议文本
     */
    fun getAdviceText(expiryTime: Long, currentTime: Long = System.currentTimeMillis()): String {
        val status = calculateFoodStatus(expiryTime, currentTime)
        
        return when (status) {
            FoodStatus.EXPIRED -> "别吃了，容易拉肚子"
            FoodStatus.EXPIRING_SOON -> "赶紧吃掉吧"
            FoodStatus.FRESH -> "可以放心吃"
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
