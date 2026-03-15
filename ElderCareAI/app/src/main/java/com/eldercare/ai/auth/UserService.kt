package com.eldercare.ai.auth

import android.content.Context
import com.eldercare.ai.data.SettingsManager
import com.eldercare.ai.data.dao.UserDao
import com.eldercare.ai.data.dao.FamilyRelationDao
import com.eldercare.ai.data.dao.FamilyLinkRequestDao
import com.eldercare.ai.data.entity.User
import com.eldercare.ai.data.entity.FamilyRelation
import com.eldercare.ai.data.entity.FamilyLinkRequestEntity
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
        
        // 检查手机号是否已注册
        val existingUser = userDao.getByPhone(phone)
        if (existingUser != null) {
            return RegisterResult.Error("该手机号已注册，请直接登录")
        }
        
        // 验证角色
        if (role != "parent" && role != "child") {
            return RegisterResult.Error("角色参数错误")
        }
        
        val now = System.currentTimeMillis()
        
        if (role == "parent") {
            // 父母端注册：生成邀请码和家庭ID
            val code = generateInviteCode()
            val familyId = generateFamilyId()
            
            val user = User(
                phone = phone,
                role = role,
                inviteCode = code,
                familyId = familyId,
                createdAt = now,
                lastLoginAt = now
            )
            
            val userId = userDao.insert(user)
            val savedUser = user.copy(id = userId)
            // 保存当前用户信息
            settingsManager.setCurrentUserId(userId)
            settingsManager.setUserRole(role)
            return RegisterResult.Success(savedUser, inviteCode = code, linkPending = false)
        } else {
            // 子女端注册：需要验证邀请码
            if (inviteCode.isNullOrBlank()) {
                return RegisterResult.Error("子女端注册需要邀请码")
            }
            
            val parentUser = userDao.getByInviteCode(inviteCode)
            if (parentUser == null) {
                return RegisterResult.Error("邀请码无效")
            }
            
            if (parentUser.role != "parent") {
                return RegisterResult.Error("邀请码无效")
            }
            
            val user = User(
                phone = phone,
                role = role,
                createdAt = now,
                lastLoginAt = now
            )
            
            val userId = userDao.insert(user)
            val savedUser = user.copy(id = userId)
            
            // 创建绑定申请（需要父母确认后才真正绑定）
            familyLinkRequestDao.insert(
                FamilyLinkRequestEntity(
                    parentUserId = parentUser.id,
                    childUserId = userId,
                    status = "pending",
                    createdAt = now
                )
            )
            
            // 保存当前用户信息（修复bug：子女端注册后也要保存角色）
            settingsManager.setCurrentUserId(userId)
            settingsManager.setUserRole(role)
            
            return RegisterResult.Success(savedUser, inviteCode = null, linkPending = true)
        }
    }
    
    /**
     * 登录
     */
    suspend fun login(phone: String): LoginResult {
        if (!isValidPhone(phone)) {
            return LoginResult.Error("手机号格式不正确")
        }
        
        val user = userDao.getByPhone(phone)
        if (user == null) {
            return LoginResult.Error("该手机号未注册，请先注册")
        }
        
        // 更新最后登录时间
        val updatedUser = user.copy(lastLoginAt = System.currentTimeMillis())
        userDao.update(updatedUser)
        
        // 保存当前用户信息
        settingsManager.setCurrentUserId(updatedUser.id)
        settingsManager.setUserRole(updatedUser.role)
        
        return LoginResult.Success(updatedUser)
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
        
        val parentUser = userDao.getByInviteCode(inviteCode)
        if (parentUser == null) {
            return LinkResult.Error("邀请码无效")
        }
        
        if (parentUser.role != "parent") {
            return LinkResult.Error("邀请码无效")
        }
        
        val existing = familyLinkRequestDao.getPending(parentUser.id, userId, "pending")
        if (existing != null) {
            return LinkResult.Pending
        }
        familyLinkRequestDao.insert(
            FamilyLinkRequestEntity(
                parentUserId = parentUser.id,
                childUserId = userId,
                status = "pending",
                createdAt = System.currentTimeMillis()
            )
        )
        return LinkResult.Pending
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
