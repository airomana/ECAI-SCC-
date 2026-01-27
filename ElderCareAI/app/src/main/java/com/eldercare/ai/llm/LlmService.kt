package com.eldercare.ai.llm

import android.content.Context
import android.util.Log
import com.eldercare.ai.llm.dashscope.*
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
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(config.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(config.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(config.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/")
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
                    return@withContext description.trim()
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
     * 构建提示词
     */
    private fun buildPrompt(
        dishName: String,
        healthProfile: com.eldercare.ai.data.entity.HealthProfile?
    ): String {
        val userName = healthProfile?.name?.takeIf { it.isNotBlank() } ?: "用户"
        val diseases = healthProfile?.diseases?.takeIf { it.isNotEmpty() }
        val allergies = healthProfile?.allergies?.takeIf { it.isNotEmpty() }
        
        return buildString {
            append("请用简单易懂的大白话描述这道菜：$dishName。")
            append("\n\n要求：")
            append("\n1. 用老人能理解的语言，避免专业术语（如高GI、碳水化合物等）")
            append("\n2. 说明主要食材和做法（如五花肉用糖和酱油烧的）")
            append("\n3. 说明特点（如很香但是油大、清淡健康等）")
            append("\n4. 如果有健康提醒，用简单的话说明（如血压高的要少吃）")
            append("\n5. 控制在50字以内，语言亲切自然")
            
            if (diseases != null || allergies != null) {
                append("\n\n用户信息：")
                if (diseases != null) {
                    append("\n- 疾病：${diseases.joinToString("、")}")
                }
                if (allergies != null) {
                    append("\n- 过敏：${allergies.joinToString("、")}")
                }
                append("\n请根据用户情况，给出个性化的健康建议。")
            }
            
            append("\n\n请直接给出描述，不要加引号或其他格式。")
        }
    }
}
