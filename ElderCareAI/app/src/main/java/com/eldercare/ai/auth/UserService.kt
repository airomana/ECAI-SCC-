package com.eldercare.ai.auth

import android.content.Context
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.dao.UserDao
import com.eldercare.ai.data.dao.FamilyRelationDao
import com.eldercare.ai.data.dao.FamilyLinkRequestDao
import com.eldercare.ai.data.entity.User
import com.eldercare.ai.data.entity.FamilyRelation
import com.eldercare.ai.data.entity.FamilyLinkRequestEntity
import com.eldercare.ai.data.network.ApiClient
import com.eldercare.ai.data.network.LoginRequest
import com.eldercare.ai.data.network.RegisterRequest
import java.security.SecureRandom
import java.util.UUID

/**
 * 用户服务
 * 处理用户注册、登录、邀请码生成和关联等业务逻辑
 */
class UserService(
    private val userDao: UserDao,
    private val familyRelationDao: FamilyRelationDao,
    private val familyLinkRequestDao: FamilyLinkRequestDao,
    private val settingsManager: SettingsManager
) {
    
    /**
     * 生成较复杂的邀请码（避免纯6位数字过于简单）
     */
    private suspend fun generateInviteCode(): String {
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val random = SecureRandom()
        repeat(10) {
            val code = buildString {
                repeat(10) {
                    append(alphabet[random.nextInt(alphabet.length)])
                    /**
     * 同步当前用户状态（例如：是否被父母确认了绑定）
     */
    suspend fun syncCurrentUserStatus() {
        val currentUser = getCurrentUser() ?: return
        try {
            // 通过登录接口获取最新的用户信息（包含 familyId）
            val apiService = ApiClient.apiService
            // For now we use dummy code "123456" as VerificationCodeService handles SMS logic separately
            val response = apiService.login(LoginRequest(currentUser.phone, "123456"))
            
            // 更新本地数据库中的 familyId
            if (response.familyId != null && response.familyId != currentUser.familyId) {
                val updatedUser = currentUser.copy(familyId = response.familyId)
                userDao.insert(updatedUser)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
            }
            val existing = userDao.getByInviteCode(code)
            if (existing == null) return code
        }
        return UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
    }
    
    /**
     * 生成唯一的家庭ID
     */
    private fun generateFamilyId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * 验证手机号格式
     */
    fun isValidPhone(phone: String): Boolean {
        return phone.matches(Regex("^1[3-9]\\d{9}$"))
    }
    
    /**
     * 注册用户
     * @param phone 手机号
     * @param role 角色：parent 或 child
     * @param inviteCode 邀请码（子女端注册时需要）
     * @return 注册结果
     */
    suspend fun register(
        phone: String,
        role: String,
        inviteCode: String? = null
    ): RegisterResult {
        // 验证手机号格式
        if (!isValidPhone(phone)) {
            return RegisterResult.Error("手机号格式不正确")
        }
        
        // 验证角色
        if (role != "parent" && role != "child") {
            return RegisterResult.Error("角色参数错误")
        }
        
        try {
            // 调用云端 API 进行注册
            val apiService = ApiClient.apiService
            val response = apiService.register(RegisterRequest(phone, role, inviteCode))
            
            val now = System.currentTimeMillis()
            
            // 将云端返回的用户信息同步到本地 Room 数据库
            val user = User(
                id = response.id,
                phone = response.phone,
                role = response.role,
                inviteCode = response.inviteCode,
                familyId = response.familyId,
                createdAt = now,
                lastLoginAt = now
            )
            
            // 使用 insertOrUpdate 策略，如果已存在则覆盖
            val existingUser = userDao.getByPhone(phone)
            if (existingUser != null) {
                 // Update instead of insert if it already exists locally, though usually room handles conflict if configured
                 userDao.insert(user) // assuming replace strategy or we just rely on the API success
            } else {
                 userDao.insert(user)
            }
            
            // 保存当前用户信息
            settingsManager.setCurrentUserId(response.id)
            settingsManager.setUserRole(response.role)
            
            // 如果提供了邀请码，尝试创建绑定申请
            if (!inviteCode.isNullOrBlank()) {
                try {
                    ApiClient.apiService.requestLink(response.id, inviteCode)
                    return RegisterResult.Success(
                        user = user,
                        inviteCode = null,
                        linkPending = true
                    )
                } catch (e: Exception) {
                    // Ignore for now, allow registration to succeed
                }
            }
            
            return RegisterResult.Success(
                user = user,
                inviteCode = response.inviteCode,
                linkPending = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return RegisterResult.Error("网络请求失败，或邀请码无效: ${e.message}")
        }
    }
    
    /**
     * 登录
     */
    suspend fun login(phone: String): LoginResult {
        try {
            val apiService = ApiClient.apiService
            // For now we use dummy code "123456" as VerificationCodeService handles SMS logic separately
            val response = apiService.login(LoginRequest(phone, "123456"))
            
            val now = System.currentTimeMillis()
            val user = User(
                id = response.id,
                phone = response.phone,
                role = response.role,
                inviteCode = response.inviteCode,
                familyId = response.familyId,
                createdAt = now,
                lastLoginAt = now
            )
            
            // Sync with local Room database
            val existingUser = userDao.getByPhone(phone)
            if (existingUser != null) {
                userDao.update(user.copy(id = existingUser.id)) // replace logic
            } else {
                userDao.insert(user)
            }
            
            // 同步健康档案和权限信息 (We don't have context here, so we skip it or fetch it differently, 
            // but the UI will trigger sync anyway on login so we can remove this line)
            
            // 保存当前用户信息
            settingsManager.setCurrentUserId(response.id)
            settingsManager.setUserRole(response.role)
            
            return LoginResult.Success(user)
        } catch (e: Exception) {
            e.printStackTrace()
            return LoginResult.Error("登录失败，请检查网络或账号是否存在")
        }
    }
    
    /**
     * 获取当前登录用户
     */
    suspend fun getCurrentUser(): User? {
        val userId = settingsManager.getCurrentUserId() ?: return null
        return userDao.getById(userId)
    }
    
    /**
     * 退出登录
     */
    fun logout() {
        settingsManager.logout()
    }
    
    /**
     * 通过邀请码关联家庭（用于已注册的子女端用户）
     */
    suspend fun linkFamilyByInviteCode(userId: Long, inviteCode: String): LinkResult {
        val childUser = userDao.getById(userId) ?: return LinkResult.Error("用户不存在")
        
        if (childUser.role != "child") {
            return LinkResult.Error("只有子女端可以关联家庭")
        }
        
        if (childUser.familyId != null) {
            return LinkResult.Error("您已经关联了家庭")
        }
        
        try {
            // Re-register with invite code to link on server
            val apiService = ApiClient.apiService
            apiService.requestLink(childUser.id, inviteCode)
            
            return LinkResult.Pending
        } catch (e: Exception) {
            e.printStackTrace()
            // Print actual error message from server if available
            return LinkResult.Error("关联失败，请检查网络或邀请码是否正确: ${e.message}")
        }
    }
    
    /**
     * 同步当前用户状态（例如：是否被父母确认了绑定）
     */
    suspend fun syncCurrentUserStatus() {
        val currentUser = getCurrentUser() ?: return
        try {
            // 通过登录接口获取最新的用户信息（包含 familyId）
            val apiService = ApiClient.apiService
            val response = apiService.login(LoginRequest(currentUser.phone, "123456"))
            
            // 更新本地数据库中的 familyId
            if (response.familyId != null && response.familyId != currentUser.familyId) {
                val updatedUser = currentUser.copy(familyId = response.familyId)
                userDao.update(updatedUser)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 根据家庭ID获取所有家庭成员
     */
    suspend fun getFamilyMembers(familyId: String): List<User> {
        return userDao.getByFamilyId(familyId)
    }
    
    /**
     * 根据用户ID获取关联的家庭成员
     */
    suspend fun getLinkedFamilyMembers(userId: Long): List<User> {
        val relations = familyRelationDao.getByUserId(userId)
        val familyIds = relations.map { it.familyId }.distinct()
        
        return familyIds.flatMap { familyId ->
            userDao.getByFamilyId(familyId)
        }.filter { it.id != userId }
    }
}

/**
 * 注册结果
 */
sealed class RegisterResult {
    data class Success(val user: User, val inviteCode: String?, val linkPending: Boolean) : RegisterResult()
    data class Error(val message: String) : RegisterResult()
}

/**
 * 登录结果
 */
sealed class LoginResult {
    data class Success(val user: User) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

/**
 * 关联家庭结果
 */
sealed class LinkResult {
    data object Pending : LinkResult()
    data class Error(val message: String) : LinkResult()
}
