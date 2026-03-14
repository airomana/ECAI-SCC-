package com.eldercare.ai.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置管理器
 * 使用SharedPreferences持久化用户设置
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LLM_ENABLED = "llm_enabled"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_USER_ROLE = "user_role"  // "parent" 或 "child"
        private const val KEY_CURRENT_USER_ID = "current_user_id"  // 当前登录用户ID
        
        private const val DEFAULT_TTS_ENABLED = true
        private const val DEFAULT_VIBRATION_ENABLED = true
        private const val DEFAULT_FONT_SIZE = 2f
        private const val DEFAULT_LLM_ENABLED = true
        private const val DEFAULT_USER_ROLE = "parent"  // 默认为父母端
    }
    
    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, DEFAULT_TTS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()
    
    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()
    
    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()
    
    // LLM相关设置
    fun isLlmEnabled(): Boolean {
        return prefs.getBoolean(KEY_LLM_ENABLED, DEFAULT_LLM_ENABLED)
    }
    
    fun setLlmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LLM_ENABLED, enabled).apply()
    }
    
    fun getLlmApiKey(): String {
        return prefs.getString(KEY_LLM_API_KEY, "") ?: ""
    }
    
    fun setLlmApiKey(apiKey: String) {
        prefs.edit().putString(KEY_LLM_API_KEY, apiKey).apply()
    }
    
    fun getLlmModel(): String {
        return prefs.getString(KEY_LLM_MODEL, "") ?: ""
    }
    
    fun setLlmModel(model: String) {
        prefs.edit().putString(KEY_LLM_MODEL, model).apply()
    }
    
    // 用户角色管理
    fun getUserRole(): String {
        return prefs.getString(KEY_USER_ROLE, DEFAULT_USER_ROLE) ?: DEFAULT_USER_ROLE
    }
    
    fun setUserRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply()
    }
    
    fun isParentRole(): Boolean {
        return getUserRole() == "parent"
    }
    
    fun isChildRole(): Boolean {
        return getUserRole() == "child"
    }
    
    // 当前登录用户ID
    fun getCurrentUserId(): Long? {
        val userId = prefs.getLong(KEY_CURRENT_USER_ID, -1L)
        return if (userId == -1L) null else userId
    }
    
    fun setCurrentUserId(userId: Long?) {
        if (userId == null) {
            prefs.edit().remove(KEY_CURRENT_USER_ID).apply()
        } else {
            prefs.edit().putLong(KEY_CURRENT_USER_ID, userId).apply()
        }
    }
    
    fun isLoggedIn(): Boolean {
        return getCurrentUserId() != null
    }
    
    fun logout() {
        setCurrentUserId(null)
        setUserRole(DEFAULT_USER_ROLE)
    }
}