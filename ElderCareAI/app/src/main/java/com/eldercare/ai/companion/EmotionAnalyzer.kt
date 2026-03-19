package com.eldercare.ai.companion

/**
 * 情绪分析器
 * 基于关键词匹配，识别老人对话中的情绪状态
 */
object EmotionAnalyzer {

    /** 支持的情绪类型 */
    enum class Emotion(val label: String, val intensity: Int) {
        HAPPY("开心", 80),
        SATISFIED("满意", 70),
        CALM("平静", 50),
        WORRIED("担心", 40),
        LONELY("孤单", 30),
        SAD("难过", 25),
        ANGRY("生气", 20),
        SICK("不适", 35)
    }

    private val happyKeywords = setOf(
        "开心", "高兴", "快乐", "愉快", "幸福", "满足", "享受", "棒", "好极了",
        "太好了", "不错", "挺好", "很好", "真好", "喜欢", "舒服", "温暖",
        "好吃", "美味", "香", "赞", "笑", "乐", "哈哈"
    )

    private val satisfiedKeywords = setOf(
        "满意", "还行", "凑合", "可以", "行", "好的", "没问题", "顺利",
        "正常", "一般般", "还好", "差不多"
    )

    private val worriedKeywords = setOf(
        "担心", "焦虑", "害怕", "紧张", "不安", "忧虑", "烦恼", "烦",
        "发愁", "愁", "怎么办", "不知道", "不确定", "担忧"
    )

    private val lonelyKeywords = setOf(
        "孤单", "寂寞", "孤独", "一个人", "没人陪", "冷清", "想念",
        "思念", "想孩子", "想家", "没人", "独自", "空荡荡"
    )

    private val sadKeywords = setOf(
        "难过", "伤心", "悲伤", "哭", "失落", "失望", "沮丧", "郁闷",
        "心情不好", "不开心", "难受", "痛苦", "委屈"
    )

    private val angryKeywords = setOf(
        "生气", "愤怒", "气死", "烦死", "讨厌", "恨", "气", "火大",
        "不满", "抱怨", "投诉"
    )

    private val sickKeywords = setOf(
        "疼", "痛", "不舒服", "难受", "生病", "头晕", "乏力", "虚弱",
        "发烧", "咳嗽", "感冒", "肚子", "腰", "腿", "手", "脚", "胸闷",
        "心慌", "气短", "没力气", "不想动"
    )

    /**
     * 分析文本情绪
     * @return Pair<情绪标签, 强度0-100>
     */
    fun analyze(text: String): Pair<String, Int> {
        if (text.isBlank()) return Pair(Emotion.CALM.label, Emotion.CALM.intensity)

        val scores = mutableMapOf<Emotion, Int>()

        fun countMatches(keywords: Set<String>): Int =
            keywords.count { text.contains(it) }

        scores[Emotion.SICK] = countMatches(sickKeywords) * 3
        scores[Emotion.LONELY] = countMatches(lonelyKeywords) * 3
        scores[Emotion.SAD] = countMatches(sadKeywords) * 3
        scores[Emotion.ANGRY] = countMatches(angryKeywords) * 3
        scores[Emotion.WORRIED] = countMatches(worriedKeywords) * 2
        scores[Emotion.HAPPY] = countMatches(happyKeywords) * 2
        scores[Emotion.SATISFIED] = countMatches(satisfiedKeywords) * 1

        val topEmotion = scores.filter { it.value > 0 }.maxByOrNull { it.value }

        return if (topEmotion != null) {
            val intensity = (topEmotion.key.intensity + topEmotion.value * 5).coerceIn(0, 100)
            Pair(topEmotion.key.label, intensity)
        } else {
            Pair(Emotion.CALM.label, Emotion.CALM.intensity)
        }
    }

    /**
     * 判断是否需要发出警报
     */
    fun needsAlert(emotion: String): Boolean {
        return emotion in setOf("孤单", "难过", "生气", "不适")
    }

    /**
     * 根据情绪列表计算主要情绪
     */
    fun dominantEmotion(emotions: List<String>): String {
        if (emotions.isEmpty()) return Emotion.CALM.label
        return emotions.groupBy { it }.maxByOrNull { it.value.size }?.key ?: Emotion.CALM.label
    }

    /**
     * 生成情绪分布 JSON 字符串
     */
    fun emotionDistributionJson(emotions: List<String>): String {
        val dist = emotions.groupBy { it }.mapValues { it.value.size }
        return dist.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }
}
