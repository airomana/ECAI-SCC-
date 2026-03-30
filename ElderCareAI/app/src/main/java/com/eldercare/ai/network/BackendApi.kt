package com.eldercare.ai.network

import retrofit2.Response
import retrofit2.http.*

// ── 通用响应包装 ──────────────────────────────────────────────────────────────
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

// ── Auth ─────────────────────────────────────────────────────────────────────
data class SendCodeRequest(val phone: String)
data class SendCodeData(val debugCode: String?)

data class RegisterRequest(
    val phone: String,
    val code: String,
    val role: String,
    val inviteCode: String? = null
)

data class LoginRequest(val phone: String, val code: String)

data class AuthData(
    val token: String,
    val userId: Long,
    val role: String,
    val inviteCode: String?,
    val familyId: String?,
    val linkPending: Boolean?
)

// ── User ─────────────────────────────────────────────────────────────────────
data class UserInfo(
    val id: Long,
    val phone: String,
    val role: String,
    val inviteCode: String,
    val familyId: String,
    val nickname: String,
    val createdAt: Long,
    val lastLoginAt: Long
)

// ── Family ───────────────────────────────────────────────────────────────────
data class LinkRequest(val inviteCode: String)
data class HandleRequest(val approve: Boolean)
data class FamilyLinkRequestData(
    val id: Long,
    val parentUserId: Long,
    val childUserId: Long,
    val status: String,
    val createdAt: Long
)

// ── Diary ────────────────────────────────────────────────────────────────────
data class DiaryEntryDto(
    val date: Long,
    val content: String,
    val emotion: String,
    val aiResponse: String
)

data class DiaryEntryRemote(
    val id: Long,
    val userId: Long,
    val date: Long,
    val content: String,
    val emotion: String,
    val aiResponse: String?
)

data class EmotionLogDto(
    val dayTimestamp: Long,
    val dominantEmotion: String,
    val emotionDistributionJson: String,
    val conversationCount: Int,
    val totalMessages: Int,
    val summary: String,
    val sentToFamily: Boolean,
    val createdAt: Long
)

// ── Retrofit Interface ────────────────────────────────────────────────────────
interface BackendApi {

    // Auth
    @POST("api/auth/send-code")
    suspend fun sendCode(@Body req: SendCodeRequest): Response<ApiResponse<SendCodeData>>

    @POST("api/auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<ApiResponse<AuthData>>

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<ApiResponse<AuthData>>

    // User
    @GET("api/user/me")
    suspend fun getMe(): Response<ApiResponse<UserInfo>>

    @PUT("api/user/profile")
    suspend fun uploadProfile(@Body body: Map<String, @JvmSuppressWildcards String>): Response<ApiResponse<Unit>>

    @GET("api/user/profile")
    suspend fun downloadProfile(): Response<ApiResponse<Map<String, String>>>

    // Family
    @POST("api/family/link")
    suspend fun requestLink(@Body req: LinkRequest): Response<ApiResponse<Unit>>

    @GET("api/family/requests")
    suspend fun getPendingRequests(): Response<ApiResponse<List<FamilyLinkRequestData>>>

    @POST("api/family/requests/{requestId}/handle")
    suspend fun handleRequest(
        @Path("requestId") requestId: Long,
        @Body req: HandleRequest
    ): Response<ApiResponse<Unit>>

    @GET("api/family/members")
    suspend fun getFamilyMembers(): Response<ApiResponse<List<UserInfo>>>

    @GET("api/family/my-requests")
    suspend fun getMyLinkRequests(): Response<ApiResponse<List<FamilyLinkRequestData>>>

    // Diary sync
    @POST("api/diary/sync")
    suspend fun syncDiaries(@Body entries: List<DiaryEntryDto>): Response<ApiResponse<Unit>>

    @POST("api/diary/emotion-log")
    suspend fun syncEmotionLog(@Body log: EmotionLogDto): Response<ApiResponse<Unit>>

    @GET("api/diary/parent")
    suspend fun getParentDiaries(@Query("since") since: Long? = null): Response<ApiResponse<List<DiaryEntryRemote>>>

    @GET("api/diary/parent/emotion-logs")
    suspend fun getParentEmotionLogs(@Query("since") since: Long? = null): Response<ApiResponse<List<EmotionLogDto>>>

    // LLM proxy
    @POST("api/llm/text")
    suspend fun llmText(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<String>

    @POST("api/llm/multimodal")
    suspend fun llmMultimodal(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<String>
}
