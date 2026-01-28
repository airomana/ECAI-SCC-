package com.eldercare.ai.auth

import kotlinx.coroutines.delay
import java.util.Random

/**
 * 验证码服务
 * 生成和验证验证码（模拟实现，实际应该通过短信服务发送）
 */
class VerificationCodeService {
    
    private val codeCache = mutableMapOf<String, CodeInfo>()
    private val random = Random()
    
    data class CodeInfo(
        val code: String,
        val phone: String,
        val expireTime: Long
    )
    
    companion object {
        private const val CODE_LENGTH = 6
        private const val CODE_EXPIRE_MINUTES = 5L
        private const val CODE_RESEND_INTERVAL_SECONDS = 60L
        
        @Volatile
        private var INSTANCE: VerificationCodeService? = null
        
        fun getInstance(): VerificationCodeService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VerificationCodeService().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 生成6位数字验证码
     */
    private fun generateCode(): String {
        return (100000..999999).random().toString()
    }
    
    /**
     * 发送验证码（模拟实现）
     * 实际应该调用短信服务API
     */
    suspend fun sendCode(phone: String): SendCodeResult {
        // 检查是否在重发间隔内
        val existing = codeCache[phone]
        if (existing != null) {
            val now = System.currentTimeMillis()
            val lastSendTime = existing.expireTime - (CODE_EXPIRE_MINUTES * 60 * 1000)
            val elapsed = (now - lastSendTime) / 1000
            if (elapsed < CODE_RESEND_INTERVAL_SECONDS) {
                val remaining = CODE_RESEND_INTERVAL_SECONDS - elapsed
                return SendCodeResult.Error("请${remaining}秒后再试")
            }
        }
        
        // 生成验证码
        val code = generateCode()
        val expireTime = System.currentTimeMillis() + CODE_EXPIRE_MINUTES * 60 * 1000
        
        // 保存验证码（实际应该通过短信发送）
        codeCache[phone] = CodeInfo(code, phone, expireTime)
        
        // 模拟发送延迟
        delay(500)
        
        // 在开发环境下，返回验证码用于测试（生产环境应该返回成功但不显示验证码）
        return SendCodeResult.Success(code, CODE_EXPIRE_MINUTES.toInt())
    }
    
    /**
     * 验证验证码
     */
    fun verifyCode(phone: String, code: String): Boolean {
        val codeInfo = codeCache[phone] ?: return false
        
        // 检查是否过期
        if (System.currentTimeMillis() > codeInfo.expireTime) {
            codeCache.remove(phone)
            return false
        }
        
        // 验证码匹配
        if (codeInfo.code == code) {
            codeCache.remove(phone) // 验证成功后删除
            return true
        }
        
        return false
    }
    
    /**
     * 清除验证码（用于测试或手动清理）
     */
    fun clearCode(phone: String) {
        codeCache.remove(phone)
    }
}

/**
 * 发送验证码结果
 */
sealed class SendCodeResult {
    data class Success(val code: String, val expireMinutes: Int) : SendCodeResult()
    data class Error(val message: String) : SendCodeResult()
}
