package com.eldercare.ai.llm

import android.content.Context
import android.util.Log
import com.eldercare.ai.llm.dashscope.*
import com.eldercare.ai.data.entity.ConversationMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * LLM服务封装
 * 用于调用阿里通义千问API生成大白话描述
 */
class LlmService private constructor(private val context: Context) {
    
    private val api: DashScopeApi
    private val config = LlmConfig
    
    init {
        // 初始化Retrofit
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY  // 开发时使用，生产环境建议改为NONE
            redactHeader("Authorization")
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(config.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(config.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(config.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://dashscope.aliyuncs.com/api/v1/services/aigc/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        api = retrofit.create(DashScopeApi::class.java)
    }
    
    companion object {
        private const val TAG = "LlmService"
        
        @Volatile
        private var INSTANCE: LlmService? = null
        
        fun getInstance(context: Context): LlmService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 为菜品生成大白话描述
     * 
     * @param dishName 菜品名称
     * @param healthProfile 用户健康档案（可选，用于个性化建议）
     * @return 大白话描述，失败时返回null
     */
    suspend fun generatePlainDescription(
        dishName: String,
        healthProfile: com.eldercare.ai.data.entity.HealthProfile? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 检查配置
            if (!config.isConfigured()) {
                Log.w(TAG, "LLM配置不完整，无法调用API")
                return@withContext null
            }
            
            // 检查是否启用
            if (!config.isEnabled(context)) {
                Log.d(TAG, "LLM功能未启用")
                return@withContext null
            }
            
            // 构建提示词
            val prompt = buildPrompt(dishName, healthProfile)
            
            // 构建请求
            val request = DashScopeRequest(
                model = config.MODEL,
                input = DashScopeInput(
                    messages = listOf(
                        DashScopeMessage(
                            role = "system",
                            content = "你是一个专业的健康饮食助手，擅长用简单易懂的大白话向老年人解释菜品。请用通俗易懂的语言，避免使用专业术语。"
                        ),
                        DashScopeMessage(
                            role = "user",
                            content = prompt
                        )
                    )
                ),
                parameters = DashScopeParameters(
                    temperature = 0.7,
                    max_tokens = 500,
                    top_p = 0.8
                )
            )
            
            // 发送请求
            val response = api.generateText(
                authorization = "Bearer ${config.API_KEY}",
                request = request
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                val description = body?.output?.choices?.firstOrNull()?.message?.content
                
                if (description != null) {
                    Log.d(TAG, "成功生成大白话描述: $description")
                    return@withContext (description as String).trim()
                } else {
                    Log.w(TAG, "响应体为空或格式不正确")
                    return@withContext null
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API调用失败: ${response.code()}, $errorBody")
                
                // 如果是余额不足或认证失败，给出更明确的提示
                when (response.code()) {
                    401, 403 -> Log.w(TAG, "API密钥无效或已过期")
                    402, 429 -> Log.w(TAG, "API余额不足或请求频率超限")
                    else -> Log.w(TAG, "API调用失败，将使用本地描述")
                }
                
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "生成大白话描述时出错", e)
            return@withContext null
        }
    }
    
    /**
     * 生成多轮对话回复（带历史上下文）
     */
    suspend fun generateConversationReply(
        userMessage: String,
        emotion: String,
        history: List<com.eldercare.ai.data.entity.ConversationMessageEntity>,
        userName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!config.isConfigured() || !config.isEnabled(context)) return@withContext null

            val name = userName?.takeIf { it.isNotBlank() } ?: "您"
            val systemPrompt = "你是一个温暖贴心的老年人陪伴助手，名叫小助手。" +
                "你的回应要：语言亲切自然像家人，用简单大白话，根据情绪给予关怀，" +
                "控制在40字以内，适合语音播报，语气温和让老人感受到陪伴。" +
                "称呼老人为\"${name}\"。"

            // 构建多轮消息历史
            val messages = mutableListOf<DashScopeMessage>()
            messages.add(DashScopeMessage(role = "system", content = systemPrompt))

            // 加入历史消息（最多6条）
            history.takeLast(6).forEach { msg ->
                messages.add(DashScopeMessage(role = msg.role, content = msg.content))
            }

            // 加入当前用户消息（附带情绪提示）
            val userPrompt = if (emotion != "平静") "$userMessage（当前情绪：$emotion）" else userMessage
            messages.add(DashScopeMessage(role = "user", content = userPrompt))

            val request = DashScopeRequest(
                model = config.MODEL,
                input = DashScopeInput(messages = messages),
                parameters = DashScopeParameters(temperature = 0.8, max_tokens = 150, top_p = 0.9)
            )

            val response = api.generateText(
                authorization = "Bearer ${config.API_KEY}",
                request = request
            )

            if (response.isSuccessful) {
                return@withContext response.body()?.output?.choices?.firstOrNull()?.message?.content?.toString()?.trim()
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "generateConversationReply error", e)
            return@withContext null
        }
    }

    /**
     * 为语音日记生成温暖的AI回应
     * 
     * @param diaryContent 日记内容（用户说的话）
     * @param emotion 识别到的情绪（满意、担心、孤单、平静等）
     * @param userName 用户名称（可选）
     * @return AI回应文本，失败时返回null
     */
    suspend fun generateDiaryResponse(
        diaryContent: String,
        emotion: String,
        userName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 检查配置
            if (!config.isConfigured()) {
                Log.w(TAG, "LLM配置不完整，无法调用API")
                return@withContext null
            }
            
            // 检查是否启用
            if (!config.isEnabled(context)) {
                Log.d(TAG, "LLM功能未启用")
                return@withContext null
            }
            
            // 构建提示词
            val prompt = buildDiaryResponsePrompt(diaryContent, emotion, userName)
            
            // 构建请求
            val request = DashScopeRequest(
                model = config.MODEL,
                input = DashScopeInput(
                    messages = listOf(
                        DashScopeMessage(
                            role = "system",
                            content = "你是一个温暖贴心的陪伴助手，专门为老年人服务。你的回应要：\n" +
                                    "1. 语言亲切自然，像家人一样温暖\n" +
                                    "2. 用简单易懂的大白话，避免专业术语\n" +
                                    "3. 根据老人的情绪给予适当的关怀和鼓励\n" +
                                    "4. 像聊天一样回应老人的话\n" +
                                    "5. 控制在30-50字以内，适合语音播报\n" +
                                    "6. 语气要温和、关心，让老人感受到陪伴"
                        ),
                        DashScopeMessage(
                            role = "user",
                            content = prompt
                        )
                    )
                ),
                parameters = DashScopeParameters(
                    temperature = 0.8,  // 稍高一些，让回应更自然
                    max_tokens = 200,
                    top_p = 0.9
                )
            )
            
            // 发送请求
            val response = api.generateText(
                authorization = "Bearer ${config.API_KEY}",
                request = request
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                val responseText = body?.output?.choices?.firstOrNull()?.message?.content
                
                if (responseText != null) {
                    Log.d(TAG, "成功生成AI回应: $responseText")
                    return@withContext (responseText as String).trim()
                } else {
                    Log.w(TAG, "响应体为空或格式不正确")
                    return@withContext null
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API调用失败: ${response.code()}, $errorBody")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "生成AI回应时出错", e)
            return@withContext null
        }
    }

    /**
     * 生成子女端的周报
     */
    suspend fun generateWeeklyReport(
        entries: List<com.eldercare.ai.data.entity.DiaryEntryEntity>,
        healthProfile: com.eldercare.ai.data.entity.HealthProfile? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!config.isConfigured() || !config.isEnabled(context)) return@withContext null
            
            val entriesText = entries.joinToString("\n") { "情绪: ${it.emotion}, 内容: ${it.content}" }
            val prompt = buildString {
                append("这是老人过去一周的陪伴聊天记录：\n$entriesText\n\n")
                if (healthProfile != null) {
                    append("老人的健康档案：姓名${healthProfile.name}，疾病${healthProfile.diseases.joinToString("、")}。\n\n")
                }
                append("请你作为陪伴助手，为老人的子女生成一份周报。要求：\n")
                append("1. 总结老人这一周的主要情绪状态\n")
                append("2. 提取老人提到的重要生活事件或需求\n")
                append("3. 给出1-2条具体的关怀建议\n")
                append("4. 语气专业、贴心，控制在150字以内")
            }
            
            val request = DashScopeRequest(
                model = config.MODEL,
                input = DashScopeInput(
                    messages = listOf(
                        DashScopeMessage(
                            role = "user",
                            content = prompt
                        )
                    )
                ),
                parameters = DashScopeParameters(
                    temperature = 0.5,
                    max_tokens = 300
                )
            )
            
            val response = api.generateText(
                authorization = "Bearer ${config.API_KEY}",
                request = request
            )
            if (response.isSuccessful) {
                return@withContext response.body()?.output?.choices?.firstOrNull()?.message?.content?.toString()?.trim()
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "生成周报出错", e)
            return@withContext null
        }
    }

    /**
     * 识别图片中的食材（使用通义千问视觉模型）
     * @param imageBase64 图片的Base64编码（不包含data:image/jpeg;base64,前缀）
     * @param visionModel 视觉模型名称（例如 qwen-vl-plus / qwen-vl-max）
     * @return 识别结果JSON字符串
     */
    suspend fun analyzeImage(imageBase64: String, visionModel: String = "qwen-vl-max"): String? = withContext(Dispatchers.IO) {
        try {
            // 检查配置
            if (!config.isConfigured()) {
                Log.w(TAG, "LLM配置不完整，无法调用API")
                return@withContext null
            }

            // 检查是否启用
            if (!config.isEnabled(context)) {
                Log.d(TAG, "LLM功能未启用")
                return@withContext null
            }

            // 构建请求
            val request = DashScopeMultimodalRequest(
                model = visionModel,
                input = DashScopeMultimodalInput(
                    messages = listOf(
                        DashScopeMultimodalMessage(
                            role = "user",
                            content = listOf(
                                mapOf("image" to "data:image/jpeg;base64,$imageBase64"),
                                mapOf(
                                    "text" to buildString {
                                        append("你在帮一位老人整理冰箱食材。请看图识别食材，并根据外观给出大致新鲜程度。")
                                        append("\n同一种食材只输出一次；如果看到多个请合并，并用 count 表示数量估计（不确定就填 1）。")
                                        append("\n必须严格只输出JSON数组（不要Markdown、不要多余文字）。格式如下：")
                                        append(
                                            "\n[\n" +
                                                "  {\n" +
                                                "    \"name\": \"食材名\",\n" +
                                                "    \"category\": \"蔬菜|水果|肉类|海鲜|蛋奶|豆制品|熟食|主食|其他\",\n" +
                                                "    \"count\": 1,\n" +
                                                "    \"clarity\": \"清楚|一般|看不清\",\n" +
                                                "    \"confidence\": 0.0,\n" +
                                                "    \"freshness\": \"新鲜|一般|快坏|疑似变质|未知\",\n" +
                                                "    \"spoil_signs_observed\": [\"发霉\"],\n" +
                                                "    \"notes\": \"可选：你判断依据（20字以内）\"\n" +
                                                "  }\n" +
                                                "]\n"
                                        )
                                        append("\n规则：")
                                        append("\n1) spoil_signs_observed 只能从以下词里选（可为空数组）：发霉|渗液|腐烂|黑斑|变色|发黏|出水|虫|破损|皱缩|软烂")
                                        append("\n2) 如果看不清：clarity=看不清，confidence<=0.3，freshness=未知，spoil_signs_observed=[]")
                                        append("\n3) 只有在 spoil_signs_observed 包含 发霉/渗液/腐烂/黑斑/软烂/发黏 时，才允许 freshness=疑似变质；否则不要随便判疑似变质")
                                        append("\n4) 如果只是轻微皱缩/出水/变色但不严重：freshness=快坏 或 一般，并写 notes 说明依据")
                                        append("\n5) 安全优先：不确定就判 freshness=未知")
                                    }
                                )
                            )
                        )
                    )
                ),
                parameters = DashScopeParameters(
                    temperature = 0.0,
                    max_tokens = 1000,
                    top_p = 0.1
                )
            )

            // 发送请求
            val response = api.generateMultimodal(
                authorization = "Bearer ${config.API_KEY}",
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                
                // 优先从 choices 获取 content
                var content = body?.output?.choices?.firstOrNull()?.message?.content
                
                // 如果 choices 为空，尝试从 text 字段获取（兼容不同版本的 qwen-vl 返回格式）
                if (content == null || (content is String && content.isBlank())) {
                    content = body?.output?.text
                } else if (content is List<*>) {
                    // 如果 content 是列表（多模态响应），尝试提取其中的文本
                    val listContent = content as List<*>
                    val textItem = listContent.find { it is Map<*, *> && it["text"] != null } as? Map<*, *>
                    if (textItem != null) {
                        content = textItem["text"] as? String
                    }
                }
                
                if (content != null && content is String) {
                    Log.d(TAG, "成功识别图片: $content")
                    
                    // 提取JSON部分（以防模型返回了Markdown代码块）
                    val jsonStart = content.indexOf("[")
                    val jsonEnd = content.lastIndexOf("]")
                    if (jsonStart != -1 && jsonEnd != -1) {
                        return@withContext content.substring(jsonStart, jsonEnd + 1)
                    }
                    return@withContext content
                } else {
                    Log.w(TAG, "响应体为空或格式不正确: ${body?.output}")
                    return@withContext null
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API调用失败: ${response.code()}, $errorBody")
                when (response.code()) {
                    401, 403 -> throw LlmAuthException("大模型API Key无效或已过期")
                    402, 429 -> throw LlmRateLimitException("大模型额度不足或请求频率过高")
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "识别图片时出错", e)
            return@withContext null
        }
    }
    
    /**
     * 构建语音日记回应的提示词
     */
    private fun buildDiaryResponsePrompt(
        diaryContent: String,
        emotion: String,
        userName: String?
    ): String {
        val name = userName?.takeIf { it.isNotBlank() } ?: "您"
        
        return buildString {
            append("一位老人刚才对我说：\"$diaryContent\"")
            append("\n\n我识别到老人的情绪是：$emotion")
            append("\n\n请用温暖亲切的语气回应老人，就像家人一样关心他。")
            append("\n\n要求：")
            append("\n1. 称呼老人为\"$name\"（如果提供了名字）或\"您\"（如果没有名字）")
            append("\n2. 对老人的分享表示理解和关心")
            append("\n3. 根据情绪给予适当的回应：")
            when (emotion) {
                "满意", "开心" -> {
                    append("\n   - 如果是开心：表达高兴，陪老人一起开心")
                }
                "担心" -> {
                    append("\n   - 如果是担心：给予安慰，缓解焦虑")
                }
                "孤单" -> {
                    append("\n   - 如果是孤单：表达理解和陪伴，建议多联系家人或找点乐子")
                }
                else -> {
                    append("\n   - 如果是平静：像闲聊一样自然回应，可以顺着话题往下聊")
                }
            }
            append("\n4. 语言要自然、口语化，适合语音播报")
            append("\n5. 控制在30-50字以内")
            append("\n\n请直接给出回应，不要加引号或其他格式。")
        }
    }
    
    /**
     * 构建提示词
     */
    private fun buildPrompt(
        dishName: String,
        healthProfile: com.eldercare.ai.data.entity.HealthProfile?
    ): String {
        val userName = healthProfile?.name?.takeIf { it.isNotBlank() } ?: "用户"
        val diseases = healthProfile?.diseases?.takeIf { it.isNotEmpty() }
        val allergies = healthProfile?.allergies?.takeIf { it.isNotEmpty() }
        val restrictions = healthProfile?.dietRestrictions?.takeIf { it.isNotEmpty() }
        
        return buildString {
            append("请用简单易懂的大白话描述这道菜：$dishName。")
            append("\n\n要求：")
            append("\n1. 用老人能理解的语言，避免专业术语（如高GI、碳水化合物等）")
            append("\n2. 说明主要食材和做法（如五花肉用糖和酱油烧的）")
            append("\n3. 说明特点（如很香但是油大、清淡健康等）")
            append("\n4. 如果有健康提醒，用简单的话说明（如血压高的要少吃）")
            append("\n5. 控制在50字以内，语言亲切自然")
            
            if (diseases != null || allergies != null || restrictions != null) {
                append("\n\n用户信息：")
                if (diseases != null) {
                    append("\n- 疾病：${diseases.joinToString("、")}")
                }
                if (allergies != null) {
                    append("\n- 过敏：${allergies.joinToString("、")}")
                }
                if (restrictions != null) {
                    append("\n- 忌口/医嘱：${restrictions.joinToString("、")}")
                }
                append("\n请根据用户情况，给出个性化的健康建议。")
            }
            
            append("\n\n请直接给出描述，不要加引号或其他格式。")
        }
    }
}
