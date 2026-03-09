package com.eldercare.ai.gemini

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini 服务
 * 用于调用 Google Gemini API 进行图像识别和分析
 */
class GeminiService(private val apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.4f
            topK = 32
            topP = 1f
            maxOutputTokens = 4096
        }
    )

    /**
     * 识别食材
     * @param bitmap 图片
     * @return 识别结果字符串
     */
    suspend fun analyzeImage(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val inputContent = content {
                image(bitmap)
                text("请分析这张图片中的食材。请列出所有你看到的食材名称，以及它们的类别（如蔬菜、水果、肉类、蛋奶等）。请以JSON格式返回，格式如下：[{\"name\": \"食材名\", \"category\": \"类别\"}]。不要包含任何其他文字或Markdown标记，只返回纯JSON字符串。")
            }

            val response = generativeModel.generateContent(inputContent)
            response.text ?: "[]"
        } catch (e: Exception) {
            e.printStackTrace()
            "[]"
        }
    }
}
