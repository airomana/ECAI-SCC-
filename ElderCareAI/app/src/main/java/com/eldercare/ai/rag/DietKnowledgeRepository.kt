package com.eldercare.ai.rag

import android.content.Context
import android.util.Log
import com.eldercare.ai.data.entity.HealthProfile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * 基于本地权威饮食知识 CSV 的 RAG 查询器
 *
 * 真实数据加载方式：
 * - 在 app 模块下新建文件：app/src/main/assets/rag/diet_knowledge.csv
 * - CSV 头部字段固定为：disease,keyword,risk_level,advice,source
 * - 每一行代表一条「疾病 × 关键词」的饮食建议
 */
data class RagRecord(
    val disease: String,
    val keyword: String,
    val riskLevel: String,   // "HIGH" / "MEDIUM" / "LOW"
    val advice: String,
    val source: String
)

class DietKnowledgeRepository private constructor(
    private val context: Context
) {

    private val loggerTag = "DietKnowledgeRepo"

    // 实际使用的记录列表：从 CSV 加载
    private val records: List<RagRecord> by lazy {
        loadFromCsv()
    }

    companion object {
        @Volatile
        private var INSTANCE: DietKnowledgeRepository? = null

        fun getInstance(context: Context): DietKnowledgeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DietKnowledgeRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * 基于老人健康档案和菜名进行“检索”
     *
     * 当前实现：
     * - 遍历 records，疾病字段和 healthProfile.diseases 模糊匹配
     * - keyword 出现在 dishName 中则认为命中
     * - 结果按风险等级从高到低排序，最多返回 topN 条
     */
    fun query(
        healthProfile: HealthProfile?,
        dishName: String,
        topN: Int = 5
    ): List<RagRecord> {
        if (healthProfile == null) return emptyList()

        val diseases = healthProfile.diseases.map { it.lowercase() }
        if (diseases.isEmpty()) return emptyList()

        val dishLower = dishName.lowercase()

        val matched = records.filter { record ->
            val recordDiseaseLower = record.disease.lowercase()
            val recordKeywordLower = record.keyword.lowercase()

            val diseaseMatch = diseases.any { userDisease ->
                userDisease.contains(recordDiseaseLower) || recordDiseaseLower.contains(userDisease)
            }

            val keywordMatch = dishLower.contains(recordKeywordLower)

            diseaseMatch && keywordMatch
        }

        if (matched.isEmpty()) {
            Log.d(loggerTag, "没有 RAG 记录匹配 dish=$dishName, diseases=${diseases.joinToString()}")
            return emptyList()
        }

        // 按风险等级排序：HIGH > MEDIUM > LOW
        val sorted = matched.sortedBy { record ->
            when (record.riskLevel.uppercase()) {
                "HIGH" -> 0
                "MEDIUM" -> 1
                else -> 2
            }
        }

        return sorted.take(topN)
    }

    /**
     * 从 assets/rag/diet_knowledge.csv 加载真实 CSV 数据
     * 若失败则返回空列表，由上层决定是否回退到 LLM
     */
    private fun loadFromCsv(): List<RagRecord> {
        val assetPath = "rag/diet_knowledge.csv"
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open(assetPath)

            BufferedReader(
                InputStreamReader(inputStream, Charset.forName("UTF-8"))
            ).use { reader ->
                val result = mutableListOf<RagRecord>()
                var lineNumber = 0

                reader.forEachLine { rawLine ->
                    val line = rawLine.trim()
                    lineNumber++

                    // 跳过空行
                    if (line.isBlank()) return@forEachLine

                    // 跳过表头（第一行）或注释行
                    if (lineNumber == 1 && line.startsWith("disease", ignoreCase = true)) {
                        return@forEachLine
                    }
                    if (line.startsWith("#")) return@forEachLine

                    // 简单 CSV 拆分：逗号分隔，允许字段中包含少量引号
                    val cells = splitCsvLine(line)
                    if (cells.size < 5) {
                        Log.w(loggerTag, "CSV 行字段不足 (line=$lineNumber): $line")
                        return@forEachLine
                    }

                    val disease = cells.getOrNull(0)?.trim().orEmpty()
                    val keyword = cells.getOrNull(1)?.trim().orEmpty()
                    val riskLevel = cells.getOrNull(2)?.trim().ifBlank { "MEDIUM" }
                    val advice = cells.getOrNull(3)?.trim().orEmpty()
                    val source = cells.getOrNull(4)?.trim().orEmpty()

                    if (disease.isBlank() || keyword.isBlank() || advice.isBlank() || source.isBlank()) {
                        Log.w(loggerTag, "CSV 行关键字段为空 (line=$lineNumber): $line")
                        return@forEachLine
                    }

                    result.add(
                        RagRecord(
                            disease = disease,
                            keyword = keyword,
                            riskLevel = riskLevel,
                            advice = advice,
                            source = source
                        )
                    )
                }

                if (result.isEmpty()) {
                    Log.w(loggerTag, "CSV 加载成功但无有效记录")
                    emptyList()
                } else {
                    Log.i(loggerTag, "已从 CSV 加载权威饮食知识记录数: ${result.size}")
                    result
                }
            }
        } catch (e: Exception) {
            Log.w(loggerTag, "加载 CSV 失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 极简 CSV 行解析：
     * - 以英文逗号分隔
     * - 支持用双引号包裹包含逗号的字段
     * - 不处理复杂转义
     */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        line.forEach { ch ->
            when (ch) {
                '"' -> {
                    inQuotes = !inQuotes
                }
                ',' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        result.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }
}

