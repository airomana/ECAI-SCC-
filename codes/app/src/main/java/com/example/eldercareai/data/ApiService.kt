package com.example.eldercareai.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API 服务接口
 * 用于连接云端大模型 API
 */
interface ApiService {
    /**
     * 识别菜品
     * @param request 包含图片 base64 编码的请求体
     */
    @POST("api/recognize")
    suspend fun recognizeDish(@Body request: RecognizeRequest): Response<RecognizeResponse>
    
    /**
     * 获取菜品营养分析
     * @param request 包含菜品名称和健康档案的请求体
     */
    @POST("api/analyze")
    suspend fun analyzeDish(@Body request: AnalyzeRequest): Response<AnalyzeResponse>
}

/**
 * 识别请求
 */
data class RecognizeRequest(
    val imageBase64: String
)

/**
 * 识别响应
 */
data class RecognizeResponse(
    val dishName: String,
    val confidence: Float
)

/**
 * 分析请求
 */
data class AnalyzeRequest(
    val dishName: String,
    val healthProfile: Map<String, String>
)

/**
 * 分析响应
 */
data class AnalyzeResponse(
    val description: String,
    val nutrition: String,
    val suitableFor: String,
    val notSuitableFor: String,
    val recommendation: String
)
