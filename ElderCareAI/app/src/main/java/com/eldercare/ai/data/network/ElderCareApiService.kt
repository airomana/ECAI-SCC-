package com.eldercare.ai.data.network

import com.eldercare.ai.data.entity.EmotionLogEntity
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// 用户认证相关请求与响应模型
data class LoginRequest(val phone: String, val code: String)
data class RegisterRequest(val phone: String, val role: String, val inviteCode: String?)
data class UserResponse(
    val id: Long,
    val phone: String,
    val role: String,
    val inviteCode: String?,
    val familyId: String?
)
data class FamilyLinkRequest(
    val id: Long,
    val parentUserId: Long,
    val childUserId: Long,
    val childPhone: String?,
    val status: String,
    val createdAt: Long,
    val handledAt: Long
)

// 周报数据模型
data class WeeklyReportResponse(
    val id: Long,
    val parentId: Long,
    val childId: Long,
    val reportContent: String,
    val generatedAt: Long
)

// 情绪日志上传请求体
data class EmotionLogRequest(
    val userId: Long,
    val emotion: String,
    val note: String,
    val timestamp: Long
)

// 测试初始化请求响应
data class InitDataResponse(
    val message: String
)

data class MessageResponse(val message: String)

data class HealthProfileRequest(
    val userId: Long,
    val name: String?,
    val age: Int?,
    val shareHealth: Boolean?,
    val shareDiet: Boolean?,
    val shareContacts: Boolean?,
    val diseases: List<String>?,
    val allergies: List<String>?
)

data class HealthProfileResponse(
    val id: Long,
    val userId: Long,
    val name: String?,
    val age: Int?,
    val shareHealth: Boolean?,
    val shareDiet: Boolean?,
    val shareContacts: Boolean?,
    val diseases: List<String>?,
    val allergies: List<String>?
)

interface ElderCareApiService {

    // 0. 用户认证与注册
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): UserResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): UserResponse

    @POST("api/auth/link")
    suspend fun requestLink(@Query("childId") childId: Long, @Query("inviteCode") inviteCode: String): MessageResponse

    @GET("api/auth/link-requests")
    suspend fun getLinkRequests(@Query("parentId") parentId: Long): List<FamilyLinkRequest>

    @POST("api/auth/handle-link")
    suspend fun handleLinkRequest(@Query("requestId") requestId: Long, @Query("accept") accept: Boolean): MessageResponse

    // 1. 获取子女的周报
    @GET("api/report/weekly")
    suspend fun getWeeklyReports(@Query("childId") childId: Long): List<WeeklyReportResponse>

    // 健康与权限同步
    @GET("api/health")
    suspend fun getHealthProfile(@Query("userId") userId: Long): HealthProfileResponse

    @POST("api/health")
    suspend fun updateHealthProfile(@Body profile: HealthProfileRequest): HealthProfileResponse

    // 情绪同步2. 上传情绪日志
    @POST("api/emotion")
    suspend fun uploadEmotionLog(@Body request: EmotionLogRequest): Any

    // 3. 测试辅助：触发周报生成
    @POST("api/test/trigger-report")
    suspend fun triggerReport(): Any

    // 4. 测试辅助：一键初始化数据
    @POST("api/test/init-data")
    suspend fun initTestData(): Any
}
