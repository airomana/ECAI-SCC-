package com.eldercare.ai.llm

import android.content.Context
import com.eldercare.ai.data.SettingsManager

/**
 * LLM配置管理
 * 用于管理阿里通义千问API的配置信息
 * 
 * 配置说明：
 * 1. API_KEY: 阿里云API密钥（从阿里云控制台获取）
 * 2. API_ENDPOINT: API端点地址（默认：https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation）
 * 3. MODEL: 使用的模型名称（默认：qwen-turbo，也可使用qwen-plus、qwen-max等）
 */
object LlmConfig {
    
    // ========== 需要配置的参数 ==========
    
    /**
     * 阿里云API密钥
     * 获取方式：
     * 1. 登录阿里云控制台：https://dashscope.console.aliyun.com/
     * 2. 进入"API-KEY管理"
     * 3. 创建新的API Key
     * 4. 将API Key填入此处（建议使用环境变量或配置文件，不要硬编码）
     */
    var API_KEY: String = "sk-6b0835b8369c47c7a5e4b8fc8d2a6d79"
        private set
    
    /**
     * API端点地址
     * 默认使用通义千问的文本生成端点
     */
    var API_ENDPOINT: String = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
        private set
    
    /**
     * 使用的模型名称
     * 可选值：
     * - qwen-turbo: 快速响应，适合实时场景
     * - qwen-plus: 平衡性能和效果
     * - qwen-max: 最强性能，适合复杂任务
     */
    var MODEL: String = "qwen-turbo"
        private set
    
    /**
     * 请求超时时间（秒）
     */
    var TIMEOUT_SECONDS: Long = 30
        private set
    
    /**
     * 是否启用LLM功能
     * 可以通过设置页面控制
     */
    fun isEnabled(context: Context): Boolean {
        return com.eldercare.ai.data.SettingsManager.getInstance(context).isLlmEnabled()
    }
    
    /**
     * 初始化配置
     * 从SharedPreferences或配置文件读取API密钥
     */
    fun initialize(context: Context) {
        val settings = com.eldercare.ai.data.SettingsManager.getInstance(context)
        
        // 从设置中读取API密钥（如果已配置）
        val savedApiKey = settings.getLlmApiKey()
        if (savedApiKey.isNotBlank()) {
            API_KEY = savedApiKey.trim()
        }
        
        // 从设置中读取模型名称（如果已配置）
        val savedModel = settings.getLlmModel()
        if (savedModel.isNotBlank()) {
            MODEL = savedModel
        }
    }
    
    /**
     * 设置API密钥
     * 建议在应用启动时或设置页面中调用
     */
    fun setApiKey(context: Context, apiKey: String) {
        API_KEY = apiKey.trim()
        com.eldercare.ai.data.SettingsManager.getInstance(context).setLlmApiKey(apiKey)
    }
    
    /**
     * 设置模型名称
     */
    fun setModel(context: Context, model: String) {
        MODEL = model
        com.eldercare.ai.data.SettingsManager.getInstance(context).setLlmModel(model)
    }
    
    /**
     * 检查配置是否完整
     */
    fun isConfigured(): Boolean {
        return API_KEY.isNotBlank() && API_ENDPOINT.isNotBlank() && MODEL.isNotBlank()
    }
}
