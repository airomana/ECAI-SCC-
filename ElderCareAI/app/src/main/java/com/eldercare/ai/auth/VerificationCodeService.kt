package com.eldercare.ai.auth

import com.eldercare.ai.data.SettingsManager
import kotlinx.coroutines.delay
import java.security.SecureRandom

/**
 * 验证码服务
 * 生成、发送、验证验证码
 */
class VerificationCodeService(
    private val settingsManager: SettingsManager
) {
    
    private val codeCache = mutableMapOf<String, CodeInfo>()
    private val random = SecureRandom()
    private val smsGatewayClient = SmsGatewayClient()
    
    data class CodeInfo(
        val code: String,
        val phone: String,
        val expireTime: Long
    )
    
    companion object {
        private const val CODE_EXPIRE_MINUTES = 5L
        private const val CODE_RESEND_INTERVAL_SECONDS = 60L
        
        @Volatile
        private var INSTANCE: VerificationCodeService? = null
        
        fun getInstance(settingsManager: SettingsManager): VerificationCodeService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VerificationCodeService(settingsManager).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 生成6位数字验证码
     */
    private fun generateCode(): String {
        val v = random.nextInt(900000) + 100000
        return v.toString()
    }
    
    /**
     * 发送验证码
     * - 未配置短信网关：本地模拟（返回测试验证码，方便联调）
     * - 配置了短信网关：调用网关发送（不返回验证码）
     */
    suspend fun sendCode(phone: String): SendCodeResult {
        val url = settingsManager.getSmsGatewayUrl().trim()

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
        
        // 先缓存，避免用户收到短信后立即校验时没有记录
        codeCache[phone] = CodeInfo(code, phone, expireTime)

        if (url.isBlank()) {
            delay(300)
            return SendCodeResult.Success(
                expireMinutes = CODE_EXPIRE_MINUTES.toInt(),
                debugCode = code
            )
        }
        
        val token = settingsManager.getSmsGatewayToken().trim().takeIf { it.isNotBlank() }
        val result = smsGatewayClient.sendCode(
            url = url,
            token = token,
            phone = phone,
            code = code,
            expireMinutes = CODE_EXPIRE_MINUTES.toInt()
        )
        return result.fold(
            onSuccess = { SendCodeResult.Success(expireMinutes = CODE_EXPIRE_MINUTES.toInt(), debugCode = null) },
            onFailure = { e ->
                codeCache.remove(phone)
                SendCodeResult.Error(e.message ?: "发送失败")
            }
        )
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
    data class Success(val expireMinutes: Int, val debugCode: String?) : SendCodeResult()
    data class Error(val message: String) : SendCodeResult()
}
