package com.eldercare.ai.llm.dashscope

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * 阿里云通义千问API接口
 * 参考文档：https://help.aliyun.com/zh/model-studio/developer-reference/api-details-9
 */
interface DashScopeApi {
    
    /**
     * 文本生成接口
     * 
     * @param authorization API密钥，格式：Bearer {API_KEY}
     * @param request 请求体
     * @return 响应结果
     */
    @POST("generation")
    suspend fun generateText(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: DashScopeRequest
    ): Response<DashScopeResponse>
}

/**
 * 请求体
 */
data class DashScopeRequest(
    val model: String,  // 模型名称，如 "qwen-turbo"
    val input: DashScopeInput,
    val parameters: DashScopeParameters? = null
)

data class DashScopeInput(
    val messages: List<DashScopeMessage>
)

data class DashScopeMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

data class DashScopeParameters(
    val temperature: Double = 0.7,  // 温度参数，控制随机性（0-1）
    val max_tokens: Int = 2000,  // 最大生成token数
    val top_p: Double = 0.8,  // 核采样参数
    val result_format: String = "message"  // 返回格式
)

/**
 * 响应体
 */
data class DashScopeResponse(
    val output: DashScopeOutput?,
    val usage: DashScopeUsage?,
    val request_id: String?
)

data class DashScopeOutput(
    val choices: List<DashScopeChoice>?,
    val finish_reason: String?
)

data class DashScopeChoice(
    val message: DashScopeMessage?,
    val finish_reason: String?
)

data class DashScopeUsage(
    val input_tokens: Int?,
    val output_tokens: Int?,
    val total_tokens: Int?
)

/**
 * 错误响应
 */
data class DashScopeError(
    val code: String?,
    val message: String?,
    val request_id: String?
)