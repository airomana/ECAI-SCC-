package com.eldercare.ai.auth

import android.content.Context
import android.util.Log
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.dao.FamilyLinkRequestDao
import com.eldercare.ai.data.dao.FamilyRelationDao
import com.eldercare.ai.data.dao.UserDao
import com.eldercare.ai.data.entity.User
import com.eldercare.ai.data.entity.FamilyLinkRequestEntity
import com.eldercare.ai.data.entity.HealthProfile
import com.eldercare.ai.data.entity.PersonalSituationEntity
import com.eldercare.ai.network.ApiClient
import com.eldercare.ai.network.BackendApi
import com.eldercare.ai.network.HandleRequest
import com.eldercare.ai.network.LinkRequest
import com.eldercare.ai.network.LoginRequest
import com.eldercare.ai.network.RegisterRequest
import com.eldercare.ai.network.SendCodeRequest
import com.google.gson.Gson
import java.security.SecureRandom
import java.util.UUID

private const val TAG = "UserService"

/**
 * 用户服务：优先走后端 API，本地 Room 作为缓存
 */
class UserService(
    private val userDao: UserDao,
    private val familyRelationDao: FamilyRelationDao,
    private val familyLinkRequestDao: FamilyLinkRequestDao,
    private val settingsManager: SettingsManager,
    private val context: Context? = null
) {
    private val api: BackendApi? by lazy {
        context?.let { ApiClient.create(it, BackendApi::class.java) }
    }

    fun isValidPhone(phone: String): Boolean =
        phone.matches(Regex("^1[3-9]\\d{9}$"))

    // ── 验证码 ────────────────────────────────────────────────────────────────

    suspend fun sendCode(phone: String): SendCodeResult {
        if (api == null) return SendCodeResult.Error("未配置服务器")
        return try {
            val resp = api!!.sendCode(SendCodeRequest(phone))
            if (resp.isSuccessful && resp.body()?.code == 200) {
                val debugCode = resp.body()?.data?.debugCode
                SendCodeResult.Success(expireMinutes = 5, debugCode = debugCode)
            } else {
                SendCodeResult.Error(resp.body()?.message ?: "发送失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendCode error", e)
            SendCodeResult.Error("网络错误：${e.message}")
        }
    }

    // ── 注册 ──────────────────────────────────────────────────────────────────

    suspend fun register(phone: String, role: String, inviteCode: String? = null): RegisterResult {
        if (!isValidPhone(phone)) return RegisterResult.Error("手机号格式不正确")

        // 后端注册
        if (api != null) {
            return try {
                val resp = api!!.register(RegisterRequest(phone, "", role, inviteCode))
                // 注意：验证码已在 VerificationCodeService 里验证，这里 code 字段由调用方传入
                // 实际上 register 时 code 由 LoginScreen 传入，见下方重载
                RegisterResult.Error("请使用 register(phone, code, role, inviteCode)")
            } catch (e: Exception) {
                RegisterResult.Error("网络错误：${e.message}")
            }
        }

        // 离线降级：本地注册
        return registerLocal(phone, role, inviteCode)
    }

    suspend fun register(phone: String, code: String, role: String, inviteCode: String? = null): RegisterResult {
        if (!isValidPhone(phone)) return RegisterResult.Error("手机号格式不正确")

        if (api != null) {
            return try {
                val resp = api!!.register(RegisterRequest(phone, code, role, inviteCode))
                val body = resp.body()
                if (resp.isSuccessful && body?.code == 200) {
                    val data = body.data!!
                    // 保存 JWT
                    settingsManager.setJwtToken(data.token)
                    settingsManager.setServerUserId(data.userId)
                    settingsManager.setUserRole(data.role)
                    ApiClient.reset()
                    // 同步到本地 Room（缓存）
                    val localUser = syncUserToLocal(data.userId, phone, data.role, data.inviteCode, data.familyId)
                    settingsManager.setCurrentUserId(localUser.id)
                    RegisterResult.Success(localUser, data.inviteCode, data.linkPending ?: false)
                } else {
                    RegisterResult.Error(body?.message ?: "注册失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "register error", e)
                RegisterResult.Error("网络错误：${e.message}")
            }
        }

        return registerLocal(phone, role, inviteCode)
    }

    // ── 登录 ──────────────────────────────────────────────────────────────────

    suspend fun login(phone: String, code: String): LoginResult {
        if (!isValidPhone(phone)) return LoginResult.Error("手机号格式不正确")

        if (api != null) {
            return try {
                val resp = api!!.login(LoginRequest(phone, code))
                val body = resp.body()
                if (resp.isSuccessful && body?.code == 200) {
                    val data = body.data!!
                    settingsManager.setJwtToken(data.token)
                    settingsManager.setServerUserId(data.userId)
                    settingsManager.setUserRole(data.role)
                    ApiClient.reset()
                    val localUser = syncUserToLocal(data.userId, phone, data.role, data.inviteCode, data.familyId)
                    settingsManager.setCurrentUserId(localUser.id)
                    LoginResult.Success(localUser)
                } else {
                    LoginResult.Error(body?.message ?: "登录失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "login error", e)
                LoginResult.Error("网络错误：${e.message}")
            }
        }

        return loginLocal(phone)
    }

    // ── 家庭绑定 ──────────────────────────────────────────────────────────────

    suspend fun linkFamilyByInviteCode(userId: Long, inviteCode: String): LinkResult {
        if (api != null) {
            return try {
                val resp = api!!.requestLink(LinkRequest(inviteCode))
                if (resp.isSuccessful && resp.body()?.code == 200) {
                    LinkResult.Pending
                } else {
                    LinkResult.Error(resp.body()?.message ?: "绑定失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "linkFamily error", e)
                LinkResult.Error("网络错误：${e.message}")
            }
        }

        // 离线降级
        return linkFamilyLocal(userId, inviteCode)
    }

    suspend fun handleLinkRequest(requestId: Long, approve: Boolean): Boolean {
        if (api == null) return false
        return try {
            val resp = api!!.handleRequest(requestId, HandleRequest(approve))
            resp.isSuccessful && resp.body()?.code == 200
        } catch (e: Exception) {
            Log.e(TAG, "handleLinkRequest error", e)
            false
        }
    }

    suspend fun getPendingLinkRequests(): List<com.eldercare.ai.network.FamilyLinkRequestData> {
        if (api == null) return emptyList()
        return try {
            val resp = api!!.getPendingRequests()
            if (resp.isSuccessful) resp.body()?.data ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── 当前用户 ──────────────────────────────────────────────────────────────

    suspend fun getCurrentUser(): User? {
        val userId = settingsManager.getCurrentUserId() ?: return null
        return userDao.getById(userId)
    }

    fun logout() {
        settingsManager.logout()
        ApiClient.reset()
    }

    // ── 本地辅助 ──────────────────────────────────────────────────────────────

    private suspend fun syncUserToLocal(
        serverId: Long, phone: String, role: String,
        inviteCode: String?, familyId: String?
    ): User {
        val existing = userDao.getByPhone(phone)
        val user = User(
            id = existing?.id ?: 0,
            phone = phone,
            role = role,
            inviteCode = inviteCode,
            familyId = familyId,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
        val localId = if (existing != null) {
            userDao.update(user.copy(id = existing.id))
            existing.id
        } else {
            userDao.insert(user)
        }
        return user.copy(id = localId)
    }

    private suspend fun registerLocal(phone: String, role: String, inviteCode: String?): RegisterResult {
        if (userDao.getByPhone(phone) != null) return RegisterResult.Error("该手机号已注册")
        val now = System.currentTimeMillis()
        val code = if (role == "parent") generateInviteCode() else null
        val familyId = if (role == "parent") UUID.randomUUID().toString() else null
        val user = User(phone = phone, role = role, inviteCode = code, familyId = familyId, createdAt = now, lastLoginAt = now)
        val id = userDao.insert(user)
        val saved = user.copy(id = id)
        settingsManager.setCurrentUserId(id)
        settingsManager.setUserRole(role)
        return RegisterResult.Success(saved, code, false)
    }

    private suspend fun loginLocal(phone: String): LoginResult {
        val user = userDao.getByPhone(phone) ?: return LoginResult.Error("该手机号未注册")
        val updated = user.copy(lastLoginAt = System.currentTimeMillis())
        userDao.update(updated)
        settingsManager.setCurrentUserId(updated.id)
        settingsManager.setUserRole(updated.role)
        return LoginResult.Success(updated)
    }

    private suspend fun linkFamilyLocal(userId: Long, inviteCode: String): LinkResult {
        val child = userDao.getById(userId) ?: return LinkResult.Error("用户不存在")
        if (child.familyId != null) return LinkResult.Error("已绑定家庭")
        val parent = userDao.getByInviteCode(inviteCode) ?: return LinkResult.Error("邀请码无效")
        val existing = familyLinkRequestDao.getPending(parent.id, userId, "pending")
        if (existing != null) return LinkResult.Pending
        familyLinkRequestDao.insert(FamilyLinkRequestEntity(
            parentUserId = parent.id, childUserId = userId,
            status = "pending", createdAt = System.currentTimeMillis()
        ))
        return LinkResult.Pending
    }

    private suspend fun generateInviteCode(): String {
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val random = SecureRandom()
        repeat(10) {
            val code = buildString { repeat(10) { append(alphabet[random.nextInt(alphabet.length)]) } }
            if (userDao.getByInviteCode(code) == null) return code
        }
        return UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
    }

    suspend fun getFamilyMembers(familyId: String): List<User> = userDao.getByFamilyId(familyId)

    /** 上传健康档案到云端 */
    suspend fun syncProfileToCloud(healthProfile: HealthProfile?, personalSituation: PersonalSituationEntity?) {
        if (context == null) return
        try {
            val gson = Gson()
            val json = gson.toJson(mapOf(
                "healthProfile" to healthProfile,
                "personalSituation" to personalSituation
            ))
            val freshApi = ApiClient.create(context, BackendApi::class.java)
            freshApi.uploadProfile(mapOf("profileJson" to json))
            Log.d(TAG, "Profile synced to cloud")
        } catch (e: Exception) {
            Log.w(TAG, "Profile sync failed", e)
        }
    }

    /** 从云端拉取健康档案，返回 JSON 字符串（由调用方解析写入 Room） */
    suspend fun pullProfileFromCloud(): String? {
        if (context == null) return null
        return try {
            // 每次直接创建，确保使用最新 JWT
            val freshApi = ApiClient.create(context, BackendApi::class.java)
            val resp = freshApi.downloadProfile()
            if (resp.isSuccessful) {
                val json = resp.body()?.data?.get("profileJson")
                Log.d(TAG, "Profile pulled from cloud: ${json?.take(50)}")
                json?.takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Profile pull failed", e)
            null
        }
    }    suspend fun getLinkedFamilyMembers(userId: Long): List<User> {
        val relations = familyRelationDao.getByUserId(userId)
        return relations.map { it.familyId }.distinct()
            .flatMap { userDao.getByFamilyId(it) }
            .filter { it.id != userId }
    }
}

sealed class RegisterResult {
    data class Success(val user: User, val inviteCode: String?, val linkPending: Boolean) : RegisterResult()
    data class Error(val message: String) : RegisterResult()
}

sealed class LoginResult {
    data class Success(val user: User) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class LinkResult {
    data object Pending : LinkResult()
    data class Error(val message: String) : LinkResult()
}
